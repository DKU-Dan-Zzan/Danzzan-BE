package com.danzzan.festival;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class HashGeneratorTest {
    @Disabled("CI 실행 제외 — 로컬 개발 시 수동 실행 전용")
    @Test
    void generateHash() {
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
        String hash = enc.encode("REDACTED_LOCAL_ONLY");
        System.out.println("HASH=" + hash);
    }
}
