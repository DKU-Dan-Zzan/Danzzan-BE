package com.danzzan.domain.user.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "jwt.secret=test-secret-key-for-jwt-at-least-32-characters-long",
        "jwt.access-validity-ms=3600000",
        "jwt.refresh-validity-ms=604800000",
        "jwt.access-token-expiration=3600000",
        "jwt.refresh-token-expiration=604800000",
        "app.cors.allowed-origins=http://localhost:5173",
        "aws.region=ap-northeast-2",
        "aws.s3.bucket=test-bucket",
        "spring.mail.host=localhost",
        "spring.mail.port=1025",
        "password-reset.mail.subject=[TEST] reset",
        "password-reset.mail.from=test@danzzan.com"
})
@AutoConfigureMockMvc
class UserSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getMyInfo_비인증_요청은_403과_빈본문을_반환한다() throws Exception {
        MvcResult result = mockMvc.perform(get("/user/me"))
                .andExpect(status().isForbidden())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).isEmpty();
    }
}
