package com.danzzan.global.security;

import com.danzzan.domain.user.model.UserInfo;
import com.danzzan.domain.user.model.entity.User;
import com.danzzan.domain.user.model.entity.UserRole;
import com.danzzan.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserAdminAuthorizationService {

    private final UserRepository userRepository;

    public boolean hasAdminRole(Authentication authentication) {
        Long userId = extractUserId(authentication);
        if (userId == null) {
            return false;
        }

        return userRepository.findById(userId)
                .map(user -> user.getRole() == UserRole.ROLE_ADMIN)
                .orElse(false);
    }

    private Long extractUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal == null || "anonymousUser".equals(principal)) {
            return null;
        }

        if (principal instanceof UserInfo userInfo) {
            return userInfo.getId();
        }
        if (principal instanceof User user) {
            return user.getId();
        }
        if (principal instanceof Long id) {
            return id;
        }
        if (principal instanceof String value) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignored) {
                return userRepository.findByStudentId(value)
                        .map(User::getId)
                        .orElse(null);
            }
        }

        return null;
    }
}
