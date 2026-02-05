package com.danzzan.ticketing.api.controller;

import com.danzzan.ticketing.api.common.ApiResponse;
import com.danzzan.ticketing.api.dto.request.AdminLoginRequestDTO;
import com.danzzan.ticketing.api.dto.response.AdminLoginResponseDTO;
import com.danzzan.ticketing.api.dto.response.AdminLogoutResponseDTO;
import com.danzzan.ticketing.api.dto.response.AdminMeResponseDTO;
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
