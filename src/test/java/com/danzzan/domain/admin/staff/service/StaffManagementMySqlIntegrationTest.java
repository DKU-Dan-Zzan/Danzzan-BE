package com.danzzan.domain.admin.staff.service;

import com.danzzan.domain.admin.staff.dto.StaffRoleChangeRequest;
import com.danzzan.domain.admin.staff.entity.StaffRoleHistory;
import com.danzzan.domain.admin.staff.repository.StaffRoleHistoryRepository;
import com.danzzan.domain.admin.staff.repository.StaffUserQueryRepository;
import com.danzzan.domain.user.model.entity.AcademicStatus;
import com.danzzan.domain.user.model.entity.User;
import com.danzzan.domain.user.model.entity.UserRole;
import com.danzzan.domain.user.repository.UserRepository;
import com.danzzan.domain.user.service.UserInfoService;
import com.danzzan.domain.user.service.UserService;
import com.danzzan.domain.user.passwordreset.config.PasswordResetProperties;
import com.danzzan.domain.user.passwordreset.dto.request.RequestPasswordResetDto;
import com.danzzan.domain.user.passwordreset.redis.PasswordResetConsumeResult;
import com.danzzan.domain.user.passwordreset.redis.PasswordResetRedisRepository;
import com.danzzan.domain.user.passwordreset.service.PasswordResetMailService;
import com.danzzan.domain.user.passwordreset.service.PasswordResetService;
import com.danzzan.domain.admin.service.AuthService;
import com.danzzan.domain.event.repository.FestivalEventRepository;
import com.danzzan.domain.ticket.repository.TicketIssueRequestRepository;
import com.danzzan.domain.ticket.repository.UserTicketRepository;
import com.danzzan.domain.ticket.service.QueueStateService;
import com.danzzan.domain.ticket.service.TicketIssueRequestStatusCacheService;
import com.danzzan.global.jwt.JwtRevocationService;
import com.danzzan.global.jwt.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * Opt-in test against a disposable MySQL schema. The URL must name a schema whose
 * name begins with staff_integration_test_; this guard prevents accidental use of a live DB.
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect",
        "app.staff-management.enabled=true",
        "jwt.refresh-token-expiration=604800000"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        StaffManagementService.class, StaffUserQueryRepository.class, StaffRoleChangeCachePublisher.class,
        UserService.class, AuthService.class, PasswordResetService.class
})
@EnabledIfEnvironmentVariable(named = "STAFF_MYSQL_TEST_URL", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StaffManagementMySqlIntegrationTest {

    private static final String URL = System.getenv("STAFF_MYSQL_TEST_URL");

    @MockitoBean private JwtRevocationService jwtRevocationService;
    @MockitoBean private UserInfoService userInfoService;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private PasswordEncoder passwordEncoder;
    @MockitoBean private UserTicketRepository userTicketRepository;
    @MockitoBean private TicketIssueRequestRepository ticketIssueRequestRepository;
    @MockitoBean private FestivalEventRepository festivalEventRepository;
    @MockitoBean private QueueStateService queueStateService;
    @MockitoBean private TicketIssueRequestStatusCacheService ticketIssueRequestStatusCacheService;
    @MockitoBean private PasswordResetProperties passwordResetProperties;
    @MockitoBean private PasswordResetRedisRepository passwordResetRedisRepository;
    @MockitoBean private PasswordResetMailService passwordResetMailService;
    @MockitoSpyBean private StaffRoleHistoryRepository historyRepository;

    @org.springframework.beans.factory.annotation.Autowired private StaffManagementService service;
    @org.springframework.beans.factory.annotation.Autowired private UserService userService;
    @org.springframework.beans.factory.annotation.Autowired private AuthService authService;
    @org.springframework.beans.factory.annotation.Autowired private PasswordResetService passwordResetService;
    @org.springframework.beans.factory.annotation.Autowired private UserRepository userRepository;
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @org.springframework.beans.factory.annotation.Autowired private jakarta.persistence.EntityManager entityManager;

    @org.springframework.beans.factory.annotation.Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private User admin;
    private User target;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        requireDisposableDatabase(URL);
        registry.add("spring.datasource.url", () -> URL);
        registry.add("spring.datasource.username", () -> requiredEnv("STAFF_MYSQL_TEST_USER"));
        registry.add("spring.datasource.password", () -> requiredEnv("STAFF_MYSQL_TEST_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @BeforeEach
    void setUp() {
        admin = userRepository.saveAndFlush(user("staff-admin", UserRole.ROLE_ADMIN));
        target = userRepository.saveAndFlush(user("staff-target", UserRole.ROLE_USER));
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("delete from user_role_change_history");
        jdbcTemplate.execute("delete from users");
    }

    @Test
    void 동시_부여는_행_잠금으로_한번만_성공하고_이력과_토큰_버전도_한번만_변경한다() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ChangeResult>> results = List.of(
                    executor.submit(() -> changeAfterStart(ready, start)),
                    executor.submit(() -> changeAfterStart(ready, start))
            );
            assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<ChangeResult> resolved = List.of(
                    results.get(0).get(10, java.util.concurrent.TimeUnit.SECONDS),
                    results.get(1).get(10, java.util.concurrent.TimeUnit.SECONDS)
            );
            assertThat(resolved).extracting(ChangeResult::success).containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }

        entityManager.clear();
        User persistedTarget = userRepository.findById(target.getId()).orElseThrow();
        assertThat(persistedTarget.getRole()).isEqualTo(UserRole.ROLE_MANAGER);
        assertThat(persistedTarget.getManagerPermissions()).containsExactly(
                com.danzzan.domain.user.model.entity.ManagerPermission.OPERATIONS,
                com.danzzan.domain.user.model.entity.ManagerPermission.TICKETING);
        assertThat(persistedTarget.getTokenVersion()).isEqualTo(1);
        Integer historyCount = jdbcTemplate.queryForObject("select count(*) from user_role_change_history", Integer.class);
        assertThat(historyCount).isEqualTo(1);
    }

    @Test
    void 이력_저장_실패는_역할과_토큰_버전까지_롤백한다() {
        doThrow(new DataIntegrityViolationException("forced history failure"))
                .when(historyRepository).save(any(StaffRoleHistory.class));

        assertThatThrownBy(() -> service.changeRole(admin.getId(), target.getId(), managerGrant()))
                .isInstanceOf(DataIntegrityViolationException.class);

        entityManager.clear();
        User persistedTarget = userRepository.findById(target.getId()).orElseThrow();
        assertThat(persistedTarget.getRole()).isEqualTo(UserRole.ROLE_USER);
        assertThat(persistedTarget.getTokenVersion()).isZero();
        Integer historyCount = jdbcTemplate.queryForObject("select count(*) from user_role_change_history", Integer.class);
        assertThat(historyCount).isZero();
    }

    @Test
    void 일반_로그아웃과_동시_회수후에도_역할은_MANAGER로_되돌아가지_않고_토큰버전은_감소하지_않는다() throws Exception {
        makeTargetManager();

        runTogether(
                () -> service.changeRole(admin.getId(), target.getId(), new StaffRoleChangeRequest("USER")),
                () -> userService.logout(target.getId())
        );

        assertRevokedWithSingleHistory(2);
    }

    @Test
    void 관리자_로그아웃과_동시_회수후에도_역할은_MANAGER로_되돌아가지_않는다() throws Exception {
        makeTargetManager();
        when(jwtTokenProvider.validateToken("stale-manager-token")).thenReturn(true);
        when(jwtTokenProvider.getUserId("stale-manager-token")).thenReturn(target.getId());
        when(jwtTokenProvider.getTokenVersion("stale-manager-token")).thenReturn(0);
        jakarta.servlet.http.HttpServletResponse response = org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletResponse.class);

        runTogether(
                () -> service.changeRole(admin.getId(), target.getId(), new StaffRoleChangeRequest("USER")),
                () -> authService.logout("stale-manager-token", response)
        );

        assertRevokedWithSingleHistoryAtLeast(1);
    }

    @Test
    void 비밀번호_재설정과_동시_회수후에도_역할은_MANAGER로_되돌아가지_않고_토큰버전은_감소하지_않는다() throws Exception {
        makeTargetManager();
        when(passwordResetRedisRepository.consumeVerifiedToken(any(), any()))
                .thenReturn(PasswordResetConsumeResult.success(target.getStudentId(), true));
        when(passwordEncoder.encode(any())).thenReturn("changed-password");

        runTogether(
                () -> service.changeRole(admin.getId(), target.getId(), new StaffRoleChangeRequest("USER")),
                () -> passwordResetService.resetPassword(new RequestPasswordResetDto("request-id", "token", "NewPass!2026", "NewPass!2026"))
        );

        assertRevokedWithSingleHistory(2);
    }

    @Test
    void 회원탈퇴가_대기중이어도_회수_커밋후_탈퇴가_같은_행잠금을_이어받아_MANAGER로_되돌릴수_없다() throws Exception {
        makeTargetManager();
        when(userTicketRepository.findAllByUserIdAndStatusForUpdate(any(), any())).thenReturn(List.of());
        when(ticketIssueRequestRepository.findAllByUserIdAndStatusForUpdate(any(), any())).thenReturn(List.of());
        when(festivalEventRepository.findAllByTicketingStatus(any())).thenReturn(List.of());
        when(passwordEncoder.encode(any())).thenReturn("withdrawn-password");
        CountDownLatch historyEntered = new CountDownLatch(1);
        CountDownLatch allowHistoryCommit = new CountDownLatch(1);
        doAnswer(invocation -> {
            historyEntered.countDown();
            if (!allowHistoryCommit.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new IllegalStateException("history release timeout");
            }
            StaffRoleHistory history = invocation.getArgument(0);
            entityManager.persist(history);
            return history;
        }).when(historyRepository).save(any(StaffRoleHistory.class));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> revoke = executor.submit(() -> service.changeRole(admin.getId(), target.getId(), new StaffRoleChangeRequest("USER")));
            assertThat(historyEntered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            Future<?> withdraw = executor.submit(() -> userService.withdraw(target.getId(), "access-token"));
            allowHistoryCommit.countDown();
            revoke.get(10, java.util.concurrent.TimeUnit.SECONDS);
            withdraw.get(10, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            allowHistoryCommit.countDown();
            executor.shutdownNow();
        }

        entityManager.clear();
        User persistedTarget = userRepository.findById(target.getId()).orElseThrow();
        assertThat(persistedTarget.isDeleted()).isTrue();
        assertThat(persistedTarget.getRole()).isEqualTo(UserRole.ROLE_USER);
        assertThat(persistedTarget.getTokenVersion()).isEqualTo(2);
        Integer historyCount = jdbcTemplate.queryForObject("select count(*) from user_role_change_history", Integer.class);
        assertThat(historyCount).isEqualTo(1);
    }

    @Test
    void promotionAndDemotionPersistEffectivePermissionsAndVersions() {
        makeTargetManager();
        service.promoteAdmin(admin.getId(), target.getId());
        assertThat(userRepository.findById(target.getId()).orElseThrow().getRole()).isEqualTo(UserRole.ROLE_ADMIN);
        service.demoteAdmin(admin.getId(), target.getId());
        User current = userRepository.findById(target.getId()).orElseThrow();
        assertThat(current.getRole()).isEqualTo(UserRole.ROLE_MANAGER);
        assertThat(current.getManagerPermissions()).hasSize(2);
        assertThat(current.getTokenVersion()).isEqualTo(2);
        assertThat(historyRepository.findAll()).hasSize(2).allSatisfy(history -> {
            assertThat(history.getPreviousPermissions()).isEqualTo("OPERATIONS,TICKETING");
            assertThat(history.getNewPermissions()).isEqualTo("OPERATIONS,TICKETING");
        });
    }

    @Test
    void concurrentCrossDemotionsLeaveOneAdminAndRejectTheDemotedActor() throws Exception {
        target.changeRole(UserRole.ROLE_ADMIN);
        userRepository.saveAndFlush(target);
        var successes = new java.util.concurrent.atomic.AtomicInteger();
        var denied = new java.util.concurrent.atomic.AtomicInteger();
        runTogether(() -> demoteOrDeny(admin.getId(), target.getId(), successes, denied),
                () -> demoteOrDeny(target.getId(), admin.getId(), successes, denied));
        assertThat(successes.get()).isEqualTo(1);
        assertThat(denied.get()).isEqualTo(1);
        assertThat(userRepository.findAll()).filteredOn(user -> !user.isDeleted() && user.getRole() == UserRole.ROLE_ADMIN).hasSize(1);
        assertThat(historyRepository.findAll()).hasSize(1);
        assertThat(userRepository.findAll()).extracting(User::getTokenVersion).containsExactlyInAnyOrder(0, 1);
    }

    private void demoteOrDeny(Long actorId, Long targetId, java.util.concurrent.atomic.AtomicInteger successes,
                              java.util.concurrent.atomic.AtomicInteger denied) {
        try {
            service.demoteAdmin(actorId, targetId);
            successes.incrementAndGet();
        } catch (com.danzzan.global.exception.AdminForbiddenException expected) {
            denied.incrementAndGet();
        }
    }

    @Test
    void preloadedManagerCannotBePromotedTwiceAfterAnotherTransactionPromotesIt() {
        assertPreloadedPromotionRejected(false);
    }

    @Test
    void preloadedManagerCannotWithdrawAfterAnotherTransactionPromotesIt() {
        assertPreloadedPromotionRejected(true);
    }

    private void assertPreloadedPromotionRejected(boolean withdraw) {
        makeTargetManager();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            assertThatThrownBy(() -> new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        assertThat(userRepository.findById(target.getId()).orElseThrow().getRole()).isEqualTo(UserRole.ROLE_MANAGER);
                        try {
                            executor.submit(() -> service.promoteAdmin(admin.getId(), target.getId())).get(10, java.util.concurrent.TimeUnit.SECONDS);
                        } catch (Exception e) { throw new IllegalStateException(e); }
                        if (withdraw) userService.withdraw(target.getId(), "old-access");
                        else service.promoteAdmin(admin.getId(), target.getId());
                    })).isInstanceOf(withdraw ? com.danzzan.global.exception.AuthException.class
                            : com.danzzan.domain.admin.staff.exception.StaffManagementException.class);
        } finally { executor.shutdownNow(); }
        User current = userRepository.findById(target.getId()).orElseThrow();
        assertThat(current.getRole()).isEqualTo(UserRole.ROLE_ADMIN);
        assertThat(current.isDeleted()).isFalse();
        assertThat(current.getTokenVersion()).isEqualTo(1);
        assertThat(historyRepository.findAll()).hasSize(1);
    }

    @Test
    void failedPromotionHistoryRollsBackRoleAndTokenVersion() {
        makeTargetManager();
        doThrow(new DataIntegrityViolationException("forced history failure"))
                .when(historyRepository).save(any(StaffRoleHistory.class));
        assertThatThrownBy(() -> service.promoteAdmin(admin.getId(), target.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        User current = userRepository.findById(target.getId()).orElseThrow();
        assertThat(current.getRole()).isEqualTo(UserRole.ROLE_MANAGER);
        assertThat(current.getTokenVersion()).isZero();
        assertThat(historyRepository.findAll()).isEmpty();
    }

    private ChangeResult changeAfterStart(CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                return new ChangeResult(false);
            }
            service.changeRole(admin.getId(), target.getId(), managerGrant());
            return new ChangeResult(true);
        } catch (com.danzzan.domain.admin.staff.exception.StaffManagementException exception) {
            return new ChangeResult(false);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new ChangeResult(false);
        }
    }

    private void runTogether(ThrowingRunnable first, ThrowingRunnable second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> one = executor.submit(() -> runAfterStart(ready, start, first));
            Future<?> two = executor.submit(() -> runAfterStart(ready, start, second));
            assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            one.get(10, java.util.concurrent.TimeUnit.SECONDS);
            two.get(10, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private void runAfterStart(CountDownLatch ready, CountDownLatch start, ThrowingRunnable runnable) {
        ready.countDown();
        try {
            if (!start.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new IllegalStateException("interleaving start timeout");
            }
            runnable.run();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void makeTargetManager() {
        target.changeRole(UserRole.ROLE_MANAGER);
        target.changeManagerPermissions(List.of(
                com.danzzan.domain.user.model.entity.ManagerPermission.OPERATIONS,
                com.danzzan.domain.user.model.entity.ManagerPermission.TICKETING));
        userRepository.saveAndFlush(target);
    }

    private StaffRoleChangeRequest managerGrant() {
        return new StaffRoleChangeRequest("MANAGER", List.of("OPERATIONS", "TICKETING"));
    }

    private void assertRevokedWithSingleHistory(int expectedTokenVersion) {
        assertRevokedWithSingleHistoryAtLeast(expectedTokenVersion);
        entityManager.clear();
        assertThat(userRepository.findById(target.getId()).orElseThrow().getTokenVersion()).isEqualTo(expectedTokenVersion);
    }

    private void assertRevokedWithSingleHistoryAtLeast(int minimumTokenVersion) {
        entityManager.clear();
        User persistedTarget = userRepository.findById(target.getId()).orElseThrow();
        assertThat(persistedTarget.getRole()).isEqualTo(UserRole.ROLE_USER);
        assertThat(persistedTarget.getManagerPermissions()).isEmpty();
        assertThat(persistedTarget.getTokenVersion()).isGreaterThanOrEqualTo(minimumTokenVersion);
        Integer historyCount = jdbcTemplate.queryForObject("select count(*) from user_role_change_history", Integer.class);
        assertThat(historyCount).isEqualTo(1);
    }

    private User user(String suffix, UserRole role) {
        return User.builder()
                .studentId("staff-" + suffix)
                .password("encoded")
                .name("통합테스트")
                .college("공과대학")
                .major("컴퓨터공학과")
                .academicStatus(AcademicStatus.ENROLLED)
                .role(role)
                .build();
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set for the MySQL integration test.");
        }
        return value;
    }

    private static void requireDisposableDatabase(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("STAFF_MYSQL_TEST_URL must be set for the MySQL integration test.");
        }
        String withoutQuery = url.substring(0, url.indexOf('?') >= 0 ? url.indexOf('?') : url.length());
        int slash = withoutQuery.lastIndexOf('/');
        String database = slash < 0 ? "" : withoutQuery.substring(slash + 1);
        if (!database.matches("staff_integration_test_[A-Za-z0-9_]+")) {
            throw new IllegalStateException("Refusing non-disposable MySQL schema: " + database);
        }
    }

    private record ChangeResult(boolean success) {
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
