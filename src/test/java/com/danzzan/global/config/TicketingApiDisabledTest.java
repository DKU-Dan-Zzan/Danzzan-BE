package com.danzzan.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 가을 축제에는 티켓팅이 없다. 프론트에서 화면을 막아도 API가 열려 있으면
 * URL을 직접 치는 사람에게는 아무 소용이 없으므로 백엔드에서도 막는다.
 *
 * 여기서 가장 중요한 검증은 마지막 케이스다. 관리자 로그인 경로(/auth/**)까지
 * 함께 막히면 축제 당일 공지를 올릴 수 없게 된다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ticketing-disabled;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=test-secret-key-for-jwt-at-least-32-characters-long",
        "jwt.access-validity-ms=3600000",
        "jwt.refresh-validity-ms=604800000",
        "jwt.access-token-expiration=3600000",
        "jwt.refresh-token-expiration=604800000",
        "app.cors.allowed-origins=http://localhost:5173",
        "app.cors.allowed-origin-patterns=http://localhost:*",
        "spring.mail.host=localhost",
        "spring.mail.port=1025",
        "password-reset.mail.subject=[TEST] reset",
        "password-reset.mail.from=test@danzzan.com",
        "octomo.api-key=test-octomo-api-key",
        "app.ticketing.api-enabled=false"
})
@AutoConfigureMockMvc
class TicketingApiDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 티켓팅_조회_API는_차단된다() throws Exception {
        int status = mockMvc.perform(get("/tickets/events"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
    }

    @Test
    void 티켓_신청_API는_차단된다() throws Exception {
        int status = mockMvc.perform(post("/tickets/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
    }

    @Test
    void 사용자_로그인_API는_차단된다() throws Exception {
        int status = mockMvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"1234\",\"password\":\"1234\"}"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
    }

    @Test
    void 관리자_로그인_경로는_차단되지_않는다() throws Exception {
        // 자격 증명이 틀려 실패하는 것은 정상이다. 확인할 것은 "보안 설정이
        // 막아서" 실패한 게 아니라 컨트롤러까지 도달했다는 사실이다.
        int status = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentNumber\":\"nobody\",\"password\":\"wrong\"}"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isNotEqualTo(403);
    }

    @Test
    void 공개_조회_API는_계속_열려있다() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));
    }
}
