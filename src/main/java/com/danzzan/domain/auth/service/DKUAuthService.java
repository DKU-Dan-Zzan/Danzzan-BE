package com.danzzan.domain.auth.service;

import com.danzzan.domain.user.exception.AlreadyStudentIdException;
import com.danzzan.domain.user.exception.CheonanCampusException;
import com.danzzan.domain.auth.dto.RequestDkuStudentDto;
import com.danzzan.domain.auth.dto.ResponseScrappedStudentInfoDto;
import com.danzzan.domain.auth.dto.ResponseVerifyStudentDto;
import com.danzzan.domain.user.model.entity.AcademicStatus;
import com.danzzan.domain.user.repository.UserRepository;
import com.danzzan.infra.dku.DkuAuthenticationService;
import com.danzzan.infra.dku.DkuStudentService;
import com.danzzan.infra.dku.model.DkuAuth;
import com.danzzan.infra.dku.model.StudentInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DKUAuthService {

    private final UserRepository userRepository;
    private final SignupService signupService;
    private final DkuAuthenticationService dkuAuthenticationService;
    private final DkuStudentService dkuStudentService;

    // 등록휴학으로 인정하는 최소 등록년월 (2026년 8월 = 2026학년도 2학기 등록분부터)
    private static final int ENROLLED_LEAVE_MIN_YEAR_MONTH = 202608;

    // 단국대 포털을 통해 학생 인증 진행
    // 1. 이미 가입된 학번인지 확인
    // 2. 단국대 포털에 로그인하여 학생 정보 크롤링
    // 3. 재학생인지 확인 후 회원가입 토큰 발급
    public ResponseVerifyStudentDto verifyStudent(RequestDkuStudentDto dto) {
        // 이미 가입된 학번인지 체크
        if (userRepository.existsByStudentId(dto.getDkuStudentId())) {
            throw new AlreadyStudentIdException();
        }

        // 단국대 포털 로그인
        DkuAuth auth = dkuAuthenticationService.login(dto.getDkuStudentId(), dto.getDkuPassword());

        // 학생 정보 크롤링
        StudentInfo studentInfo = dkuStudentService.crawlStudentInfo(auth);

        // 학적 상태 변환
        AcademicStatus academicStatus = parseAcademicStatus(studentInfo.getAcademicStatus());

        // 휴학인데 2026년 8월 이후에 등록한 경우(등록 후 휴학)만 등록휴학으로 처리
        if (academicStatus == AcademicStatus.LEAVE
                && isEnrolledLeaveRegistrationDate(studentInfo.getRegistrationDate())) {
            academicStatus = AcademicStatus.ENROLLED_LEAVE;
        }

        // 죽전캠퍼스 학생만 가입 가능 (학부: 3xxxxx, 대학원: 7xxxxx)
        validateJukjeonCampus(studentInfo.getStudentId(), studentInfo.getCollege());

        // 재학생·수료생·졸업유예생·등록휴학생만 가입 가능
        if (academicStatus != AcademicStatus.ENROLLED
                && academicStatus != AcademicStatus.COMPLETED
                && academicStatus != AcademicStatus.GRADUATION_DEFERRED
                && academicStatus != AcademicStatus.ENROLLED_LEAVE) {
            throw new IllegalStateException("재학생, 수료생, 졸업유예생, 등록휴학생만 회원가입이 가능합니다.");
        }

        // 회원가입 토큰 생성
        String signupToken = UUID.randomUUID().toString();

        // 학생 정보 캐시에 저장
        signupService.cacheStudentInfo(
                signupToken,
                studentInfo.getStudentId(),
                studentInfo.getStudentName(),
                studentInfo.getCollege(),
                studentInfo.getMajor(),
                academicStatus
        );

        // 응답 DTO 생성
        ResponseScrappedStudentInfoDto studentDto = new ResponseScrappedStudentInfoDto(
                studentInfo.getStudentName(),
                studentInfo.getStudentId(),
                studentInfo.getCollege(),
                studentInfo.getMajor()
        );

        return new ResponseVerifyStudentDto(signupToken, studentDto);
    }

    // 회원가입 토큰으로 캐시에서 학생 정보 조회
    public ResponseScrappedStudentInfoDto getStudentInfo(String signupToken) {
        SignupService.StudentInfoCache cache = signupService.getCachedStudentInfo(signupToken);
        return new ResponseScrappedStudentInfoDto(
                cache.name(), cache.studentId(), cache.college(), cache.major()
        );
    }

    // 천안캠퍼스 단과대학 키워드 블랙리스트 (죽전에 없는 단과대학명 기준)
    private static final List<String> CHEONAN_COLLEGE_KEYWORDS = List.of(
            "간호", "의과", "치과", "약학", "과학기술", "바이오융합", "스포츠", "외국어", "공공", "보건", "생명공학"
    );

    // 죽전캠퍼스 학부생만 가입 가능
    // - 학부생(3xxxxx): 단과대학명이 천안 블랙리스트에 없으면 죽전으로 판단
    // - 대학원생(7xxxxx): 가입 차단
    // - 그 외 학번: 차단
    private void validateJukjeonCampus(String studentId, String college) {
        boolean isUndergrad = studentId.startsWith("3");

        if (!isUndergrad) {
            throw new CheonanCampusException();
        }

        String collegeName = college != null ? college : "";
        boolean isCheonan = CHEONAN_COLLEGE_KEYWORDS.stream()
                .anyMatch(collegeName::contains);

        // 예술대학(천안) vs 음악·예술대학(죽전) 별도 구분
        boolean isCheonanArts = collegeName.contains("예술대학") && !collegeName.contains("음악·예술대학");

        if (isCheonan || isCheonanArts) {
            throw new CheonanCampusException();
        }
    }

    // 등록일자가 "등록휴학"으로 인정되는 시점인지 판별한다.
    //
    // 휴학생 중에서도 2026년 8월(2026학년도 2학기) 이후에 등록을 마치고 휴학한 학생만
    // 등록휴학으로 인정한다. 즉 학적상태가 "휴학"이면서 등록일자가 "2026-08-xx" 이후여야 한다.
    //
    // 등록일자는 포털에 따라 "20260811" / "2026-08-11" / "2026.08.11" 등으로 내려올 수 있으므로
    // 숫자만 추려 앞 6자리(yyyyMM)를 기준년월과 비교한다.
    private boolean isEnrolledLeaveRegistrationDate(String registrationDate) {
        if (registrationDate == null) {
            return false;
        }

        String digits = registrationDate.replaceAll("[^0-9]", "");
        if (digits.length() < 6) {
            log.warn("등록일자 형식을 인식할 수 없음: {}", registrationDate);
            return false;
        }

        try {
            int yearMonth = Integer.parseInt(digits.substring(0, 6));
            return yearMonth >= ENROLLED_LEAVE_MIN_YEAR_MONTH;
        } catch (NumberFormatException e) {
            log.warn("등록일자 파싱 실패: {}", registrationDate);
            return false;
        }
    }

    // 학적 상태 문자열을 AcademicStatus enum으로 변환
    // "재학", "휴학", "졸업" 등의 문자열을 파싱
    private AcademicStatus parseAcademicStatus(String status) {
        if (status == null || status.isEmpty()) {
            return AcademicStatus.ENROLLED;
        }

        String normalized = status.trim();

        if (normalized.contains("재학") || normalized.contains("재籍") || normalized.equals("ENROLLED")) {
            return AcademicStatus.ENROLLED;
        } else if (normalized.contains("휴학") || normalized.equals("LEAVE")) {
            return AcademicStatus.LEAVE;
        } else if (normalized.contains("졸업유예") || normalized.equals("GRADUATION_DEFERRED")) {
            return AcademicStatus.GRADUATION_DEFERRED;
        } else if (normalized.contains("졸업") || normalized.equals("GRADUATED")) {
            return AcademicStatus.GRADUATED;
        } else if (normalized.contains("수료") || normalized.equals("COMPLETED")) {
            return AcademicStatus.COMPLETED;
        }

        // 알 수 없는 상태는 일단 재학으로 처리
        log.warn("알 수 없는 학적 상태: {}", status);
        return AcademicStatus.ENROLLED;
    }
}
