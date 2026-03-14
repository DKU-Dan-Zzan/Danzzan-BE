package com.danzzan.festival;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class HashGeneratorTest {
    @Test
    void generateHash() {
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
        String hash = enc.encode("test1234");
        System.out.println("HASH=" + hash);
    }
}
