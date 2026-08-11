package com.danzzan.domain.auth.service;

import com.danzzan.domain.auth.dto.RequestDkuStudentDto;
import com.danzzan.domain.user.model.entity.AcademicStatus;
import com.danzzan.domain.user.repository.UserRepository;
import com.danzzan.infra.dku.DkuAuthenticationService;
import com.danzzan.infra.dku.DkuStudentService;
import com.danzzan.infra.dku.model.DkuAuth;
import com.danzzan.infra.dku.model.StudentInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 회원가입 가능 학적상태 판별 로직 테스트.
 *
 * <p>정책: 재학생과 "2026학년도 2학기 등록휴학생"만 가입 가능하다.
 * 등록휴학은 학적상태가 "휴학"이면서 등록일자가 2026년 8월 이후인 경우로 판별한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class DKUAuthServiceTest {

    private static final String STUDENT_ID = "32211862";
    private static final String PASSWORD = "dku_password";

    @Mock
    private UserRepository userRepository;

    @Mock
    private SignupService signupService;

    @Mock
    private DkuAuthenticationService dkuAuthenticationService;

    @Mock
    private DkuStudentService dkuStudentService;

    private DKUAuthService service;

    @BeforeEach
    void setUp() {
        service = new DKUAuthService(
                userRepository,
                signupService,
                dkuAuthenticationService,
                dkuStudentService
        );
    }

    // ── 재학생 ────────────────────────────────────────────────────────────────

    @Test
    void verifyStudent_재학생은_가입할수있다() {
        givenCrawledStudent("재학", "20260311");

        service.verifyStudent(request());

        verifyCachedWith(AcademicStatus.ENROLLED);
    }

    // ── 등록휴학 인정 ─────────────────────────────────────────────────────────

    @Test
    void verifyStudent_휴학이어도_2026년8월_등록이면_등록휴학으로_가입할수있다() {
        givenCrawledStudent("휴학", "20260811");

        service.verifyStudent(request());

        verifyCachedWith(AcademicStatus.ENROLLED_LEAVE);
    }

    @Test
    void verifyStudent_등록일자에_구분자가_있어도_등록휴학으로_인정한다() {
        givenCrawledStudent("휴학", "2026-08-11");

        service.verifyStudent(request());

        verifyCachedWith(AcademicStatus.ENROLLED_LEAVE);
    }

    @Test
    void verifyStudent_2026년8월_이후_등록도_등록휴학으로_인정한다() {
        givenCrawledStudent("휴학", "20260901");

        service.verifyStudent(request());

        verifyCachedWith(AcademicStatus.ENROLLED_LEAVE);
    }

    // ── 등록휴학 불인정 ───────────────────────────────────────────────────────

    @Test
    void verifyStudent_2026년1학기_등록후_휴학은_가입할수없다() {
        givenCrawledStudent("휴학", "20260311");

        assertThatThrownBy(() -> service.verifyStudent(request()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("등록휴학생");

        verifyNotCached();
    }

    @Test
    void verifyStudent_이전연도_8월_등록후_휴학은_가입할수없다() {
        givenCrawledStudent("휴학", "20250811");

        assertThatThrownBy(() -> service.verifyStudent(request()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("등록휴학생");

        verifyNotCached();
    }

    /** 등록일자가 없거나 yyyyMM을 읽을 수 없으면 등록휴학으로 인정하지 않는다(NPE 없이 거절). */
    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "2026", "알 수 없음"})
    void verifyStudent_등록일자를_읽을수없는_휴학생은_가입할수없다(String registrationDate) {
        givenCrawledStudent("휴학", registrationDate);

        assertThatThrownBy(() -> service.verifyStudent(request()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("등록휴학생");

        verifyNotCached();
    }

    @Test
    void verifyStudent_등록일자가_null인_휴학생은_가입할수없다() {
        givenCrawledStudent("휴학", null);

        assertThatCode(() -> service.verifyStudent(request()))
                .isInstanceOf(IllegalStateException.class);

        verifyNotCached();
    }

    // ── 그 외 학적상태 ────────────────────────────────────────────────────────

    /** 수료·졸업유예는 이전 정책에서는 가입 가능했으나 재학·등록휴학만 허용하도록 축소되었다. */
    @ParameterizedTest
    @ValueSource(strings = {"수료", "졸업유예", "졸업"})
    void verifyStudent_재학_등록휴학이_아니면_가입할수없다(String academicStatus) {
        givenCrawledStudent(academicStatus, "20260811");

        assertThatThrownBy(() -> service.verifyStudent(request()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("회원가입이 가능합니다");

        verifyNotCached();
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private RequestDkuStudentDto request() {
        return new RequestDkuStudentDto(STUDENT_ID, PASSWORD);
    }

    /** 미가입 학번으로 로그인에 성공하고, 주어진 학적상태·등록일자가 크롤링되는 상황을 만든다. */
    private void givenCrawledStudent(String academicStatus, String registrationDate) {
        DkuAuth auth = new DkuAuth();
        when(userRepository.existsByStudentId(STUDENT_ID)).thenReturn(false);
        when(dkuAuthenticationService.login(STUDENT_ID, PASSWORD)).thenReturn(auth);
        when(dkuStudentService.crawlStudentInfo(auth)).thenReturn(new StudentInfo(
                "홍길동",
                STUDENT_ID,
                "공과대학",          // 죽전캠퍼스 단과대학
                "컴퓨터공학과",
                academicStatus,
                2022,
                registrationDate
        ));
    }

    private void verifyCachedWith(AcademicStatus expected) {
        verify(signupService).cacheStudentInfo(
                anyString(), eq(STUDENT_ID), anyString(), anyString(), anyString(), eq(expected)
        );
    }

    private void verifyNotCached() {
        verify(signupService, never()).cacheStudentInfo(
                anyString(), anyString(), anyString(), anyString(), anyString(), any()
        );
    }
}
