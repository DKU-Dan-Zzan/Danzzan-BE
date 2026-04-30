package com.danzzan.domain.user.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:user-security;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
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
        "app.cors.allowed-origin-patterns=http://localhost:*,http://127.0.0.1:*,http://10.*:*,http://172.*:*,http://192.168.*:*,http://169.254.*:*",
        "spring.mail.host=localhost",
        "spring.mail.port=1025",
        "password-reset.mail.subject=[TEST] reset",
        "password-reset.mail.from=test@danzzan.com",
        "octomo.api-key=test-octomo-api-key"
})
@AutoConfigureMockMvc
class UserSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getMyInfo_비인증_요청은_401과_빈본문을_반환한다() throws Exception {
        MvcResult result = mockMvc.perform(get("/user/me"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).isEmpty();
    }

    @Test
    void dkuVerify_개발용_LAN_Origin_프리플라이트를_허용한다() throws Exception {
        mockMvc.perform(options("/user/dku/verify")
                        .header("Origin", "http://172.31.93.16:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://172.31.93.16:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }
}
