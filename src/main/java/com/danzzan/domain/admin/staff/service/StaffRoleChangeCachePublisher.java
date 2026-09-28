package com.danzzan.domain.admin.staff.service;

import com.danzzan.global.jwt.JwtRevocationService;
import com.danzzan.domain.user.service.UserInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StaffRoleChangeCachePublisher {
    private final JwtRevocationService jwtRevocationService;
    private final UserInfoService userInfoService;

    public void publishAfterCommit(Long userId, int tokenVersion) {
        jwtRevocationService.runAfterCommit(userId, "staff-role-change", () -> {
            userInfoService.invalidateUserInfo(userId);
            jwtRevocationService.cacheUserVersion(userId, tokenVersion);
        });
    }
}
