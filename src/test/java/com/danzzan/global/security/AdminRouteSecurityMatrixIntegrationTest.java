package com.danzzan.global.security;

import com.danzzan.domain.user.model.entity.AcademicStatus;
import com.danzzan.domain.user.model.entity.User;
import com.danzzan.domain.user.model.entity.UserRole;
import com.danzzan.domain.user.model.entity.ManagerPermission;
import com.danzzan.domain.user.repository.UserRepository;
import com.danzzan.global.jwt.JwtRevocationService;
import com.danzzan.global.jwt.JwtTokenProvider;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-security-matrix;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=test-secret-key-for-jwt-at-least-32-characters-long",
        "jwt.access-token-expiration=3600000",
        "jwt.refresh-token-expiration=604800000",
        "app.cors.allowed-origins=http://localhost:5173",
        "app.cors.allowed-origin-patterns=http://localhost:*,http://127.0.0.1:*",
        "spring.mail.host=localhost",
        "spring.mail.port=1025",
        "password-reset.mail.subject=[TEST] reset",
        "password-reset.mail.from=test@danzzan.com",
        "octomo.api-key=test-octomo-api-key"
})
@AutoConfigureMockMvc
class AdminRouteSecurityMatrixIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(AdminRouteSecurityMatrixIntegrationTest.class);

    @Autowired private MockMvc mockMvc;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlerMapping;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;

    @MockitoBean private JwtRevocationService jwtRevocationService;

    private String adminToken;
    private String managerToken;
    private String operationsManagerToken;
    private String ticketingManagerToken;
    private String noPermissionManagerToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        User admin = save("admin", UserRole.ROLE_ADMIN);
        User manager = save("manager", UserRole.ROLE_MANAGER);
        manager.changeManagerPermissions(List.of(ManagerPermission.OPERATIONS, ManagerPermission.TICKETING));
        manager = userRepository.save(manager);
        User operationsManager = save("operations-manager", UserRole.ROLE_MANAGER);
        operationsManager.changeManagerPermissions(List.of(ManagerPermission.OPERATIONS));
        operationsManager = userRepository.save(operationsManager);
        User ticketingManager = save("ticketing-manager", UserRole.ROLE_MANAGER);
        ticketingManager.changeManagerPermissions(List.of(ManagerPermission.TICKETING));
        ticketingManager = userRepository.save(ticketingManager);
        User noPermissionManager = save("no-permission-manager", UserRole.ROLE_MANAGER);
        User user = save("user", UserRole.ROLE_USER);
        adminToken = token(admin);
        managerToken = token(manager);
        operationsManagerToken = token(operationsManager);
        ticketingManagerToken = token(ticketingManager);
        noPermissionManagerToken = token(noPermissionManager);
        userToken = token(user);
    }

    @Test
    void everyMappedAdminRouteEnforcesTheAdminManagerUserMatrix() throws Exception {
        List<AdminRoute> routes = adminRoutes();
        log.info("admin authorization matrix mappedRoutes={}", routes.size());
        for (AdminRoute route : routes) {
            int anonymous = status(route, null);
            int user = status(route, userToken);
            int manager = status(route, managerToken);
            int operationsManager = status(route, operationsManagerToken);
            int ticketingManager = status(route, ticketingManagerToken);
            int noPermissionManager = status(route, noPermissionManagerToken);
            int admin = status(route, adminToken);

            assertThat(anonymous).as(route + " anonymous").isEqualTo(401);
            assertThat(user).as(route + " user").isEqualTo(403);
            assertThat(admin).as(route + " admin").isNotIn(401, 403);
            if (route.staffOnly()) {
                assertThat(manager).as(route + " manager").isEqualTo(403);
                assertThat(operationsManager).as(route + " operations manager").isEqualTo(403);
                assertThat(ticketingManager).as(route + " ticketing manager").isEqualTo(403);
            } else {
                assertThat(manager).as(route + " manager").isNotIn(401, 403);
                assertThat(noPermissionManager).as(route + " no permission manager").isEqualTo(403);
                if (route.ticketingOnly()) {
                    assertThat(ticketingManager).as(route + " ticketing manager").isNotIn(401, 403);
                    assertThat(operationsManager).as(route + " operations manager").isEqualTo(403);
                } else {
                    assertThat(operationsManager).as(route + " operations manager").isNotIn(401, 403);
                    assertThat(ticketingManager).as(route + " ticketing manager").isEqualTo(403);
                }
            }
        }
    }

    private List<AdminRoute> adminRoutes() {
        return handlerMapping.getHandlerMethods().entrySet().stream()
                .flatMap(entry -> entry.getKey().getPatternValues().stream()
                        .filter(this::isAdminPath)
                        .flatMap(path -> methods(entry).stream().map(method -> new AdminRoute(method, concretePath(path)))))
                .distinct()
                .sorted(Comparator.comparing(AdminRoute::path).thenComparing(route -> route.method().name()))
                .toList();
    }

    private List<HttpMethod> methods(Map.Entry<org.springframework.web.servlet.mvc.method.RequestMappingInfo, HandlerMethod> entry) {
        var methods = entry.getKey().getMethodsCondition().getMethods();
        return methods.isEmpty() ? List.of(HttpMethod.GET) : methods.stream()
                .map(method -> HttpMethod.valueOf(method.name()))
                .toList();
    }

    private boolean isAdminPath(String path) {
        return isPathOrChild(path, "/api/admin")
                || isPathOrChild(path, "/admin/map")
                || isPathOrChild(path, "/admin/timetable")
                || isPathOrChild(path, "/admin/festival");
    }

    private boolean isPathOrChild(String path, String basePath) {
        return path.equals(basePath) || path.startsWith(basePath + "/");
    }

    private String concretePath(String path) {
        return path.replaceAll("\\{[^/]+}", "1");
    }

    private int status(AdminRoute route, String token) throws Exception {
        MockHttpServletRequestBuilder request = request(route.method(), route.path())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        try {
            return mockMvc.perform(request).andReturn().getResponse().getStatus();
        } catch (ServletException e) {
            if (token != null && hasMultipartBindingFailure(e)) {
                // Multipart binding runs only after the security chain accepted this request.
                return 400;
            }
            throw e;
        }
    }

    private boolean hasMultipartBindingFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof MultipartException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private User save(String studentId, UserRole role) {
        return userRepository.save(User.builder()
                .studentId(studentId)
                .password("encoded-password")
                .name(studentId)
                .college("college")
                .major("major")
                .academicStatus(AcademicStatus.ENROLLED)
                .role(role)
                .build());
    }

    private String token(User user) {
        return jwtTokenProvider.createAccessToken(user.getId(), user.getStudentId(), user.getRole().name(),
                user.getTokenVersion(), user.getManagerPermissions().stream().map(ManagerPermission::name).toList());
    }

    private record AdminRoute(HttpMethod method, String path) {
        boolean staffOnly() {
            return path.startsWith("/api/admin/staff");
        }

        boolean ticketingOnly() {
            return path.startsWith("/api/admin/events") || path.equals("/admin/festival/ticketing-settings");
        }
    }
}
