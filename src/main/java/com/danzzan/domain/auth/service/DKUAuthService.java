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

        // 죽전캠퍼스 학생만 가입 가능 (학부: 3xxxxx, 대학원: 7xxxxx)
        validateJukjeonCampus(studentInfo.getStudentId(), studentInfo.getCollege());

        // 재학생·수료생만 가입 가능
        if (academicStatus != AcademicStatus.ENROLLED && academicStatus != AcademicStatus.COMPLETED) {
            throw new IllegalStateException("재학생 및 수료생만 회원가입이 가능합니다.");
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
            "간호", "의과", "치과", "약학", "과학기술", "바이오융합", "스포츠", "외국어", "공공", "보건"
    );

    // 죽전캠퍼스 학생 여부 검증
    // - 학부생(3xxxxx): 단과대학명이 천안 블랙리스트에 없으면 죽전으로 판단
    // - 대학원생(7xxxxx): 단과대학명이 천안 블랙리스트에 없으면 죽전으로 판단
    // - 그 외 학번: 차단
    private void validateJukjeonCampus(String studentId, String college) {
        boolean isUndergrad = studentId.startsWith("3");
        boolean isGrad = studentId.startsWith("7");

        if (!isUndergrad && !isGrad) {
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
