package com.danzzan.ticketing.domain.admin.auth.controller;

import com.danzzan.ticketing.global.model.ApiResponse;
import com.danzzan.ticketing.domain.admin.auth.dto.AdminLoginRequestDTO;
import com.danzzan.ticketing.domain.admin.auth.dto.AdminLoginResponseDTO;
import com.danzzan.ticketing.domain.admin.auth.dto.AdminLogoutResponseDTO;
import com.danzzan.ticketing.domain.admin.auth.dto.AdminMeResponseDTO;
import com.danzzan.ticketing.domain.admin.auth.service.AdminAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @PostMapping("/auth/login")
    public ApiResponse<AdminLoginResponseDTO> login(@Valid @RequestBody AdminLoginRequestDTO request) {
        return ApiResponse.success(adminAuthService.login(request));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/auth/me")
    public ApiResponse<AdminMeResponseDTO> me(HttpServletRequest request) {
        String accessToken = extractAccessToken(request);
        return ApiResponse.success(adminAuthService.me(accessToken));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/auth/logout")
    public ApiResponse<AdminLogoutResponseDTO> logout(HttpServletRequest request) {
        String accessToken = extractAccessToken(request);
        return ApiResponse.success(adminAuthService.logout(accessToken));
    }

    private String extractAccessToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
