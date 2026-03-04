package com.danzzan.domain.admin.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.danzzan.domain.admin.dto.request.LoginRequest;
import com.danzzan.domain.admin.dto.response.LogoutResponse;
import com.danzzan.domain.admin.dto.response.TokenResponse;
import com.danzzan.domain.admin.service.AuthService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        String accessToken = authService.login(
                request.getStudentNumber(),
                request.getPassword(),
                response
        );
        return ResponseEntity.ok(new TokenResponse(accessToken));
    }

    @PostMapping("/reissue")
    public ResponseEntity<TokenResponse> reissue(
            @CookieValue(name = "refreshToken", required = false) String refreshToken
    ) {
        String newAccess = authService.reissue(refreshToken);
        return ResponseEntity.ok(new TokenResponse(newAccess));
    }

    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        authService.logout(refreshToken, response);
        return ResponseEntity.ok(LogoutResponse.ok());
    }
}
