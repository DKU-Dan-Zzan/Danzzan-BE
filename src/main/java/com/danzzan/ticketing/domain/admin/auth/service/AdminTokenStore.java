package com.danzzan.ticketing.domain.admin.auth.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class AdminTokenStore {

    private final Map<String, Long> tokenToUserId = new ConcurrentHashMap<>();

    public String issueToken(Long userId) {
        String token = "admin-access-token-temp-" + UUID.randomUUID();
        tokenToUserId.put(token, userId);
        return token;
    }

    public Long getUserId(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return tokenToUserId.get(token);
    }

    public void revoke(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        tokenToUserId.remove(token);
    }
}
