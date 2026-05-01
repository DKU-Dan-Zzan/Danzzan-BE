package com.danzzan.domain.admin.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.danzzan.domain.admin.dto.request.LoginRequest;
import com.danzzan.domain.admin.dto.request.RefreshRequest;
import com.danzzan.domain.admin.dto.response.LogoutResponse;
import com.danzzan.domain.admin.dto.response.TokenResponse;
import com.danzzan.domain.admin.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse tokens = authService.login(
                request.getStudentNumber(),
                request.getPassword()
        );
        return ResponseEntity.ok(tokens);
    }

    @PostMapping("/reissue")
    public ResponseEntity<TokenResponse> reissue(@RequestBody RefreshRequest request) {
        TokenResponse tokens = authService.reissue(request.getRefreshToken());
        return ResponseEntity.ok(tokens);
    }

    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(@RequestBody(required = false) RefreshRequest request) {
        authService.logout(request != null ? request.getRefreshToken() : null);
        return ResponseEntity.ok(LogoutResponse.ok());
    }
}
