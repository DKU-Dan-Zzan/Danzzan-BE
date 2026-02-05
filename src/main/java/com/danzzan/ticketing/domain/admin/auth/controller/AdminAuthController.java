package com.danzzan.ticketing.domain.admin.auth.controller;

import com.danzzan.ticketing.global.model.ApiResponse;
import com.danzzan.ticketing.domain.admin.auth.dto.AdminLoginRequestDTO;
import com.danzzan.ticketing.domain.admin.auth.dto.AdminLoginResponseDTO;
import com.danzzan.ticketing.domain.admin.auth.dto.AdminLogoutResponseDTO;
import com.danzzan.ticketing.domain.admin.auth.dto.AdminMeResponseDTO;
import jakarta.validation.Valid;
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
public interface AdminAuthController {

    @PostMapping("/auth/login")
    ApiResponse<AdminLoginResponseDTO> login(@Valid @RequestBody AdminLoginRequestDTO request);

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/auth/me")
    ApiResponse<AdminMeResponseDTO> me();

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/auth/logout")
    ApiResponse<AdminLogoutResponseDTO> logout();
}
