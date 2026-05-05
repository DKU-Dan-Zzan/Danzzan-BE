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
        String[] tokens = authService.login(
                request.getStudentNumber(),
                request.getPassword(),
                response
        );
        return ResponseEntity.ok(new TokenResponse(tokens[0], tokens[1]));
    }

    @PostMapping("/reissue")
    public ResponseEntity<TokenResponse> reissue(
            @CookieValue(name = "refreshToken", required = false) String cookieRefreshToken,
            @RequestBody(required = false) java.util.Map<String, String> body
    ) {
        String refreshToken = cookieRefreshToken;
        if ((refreshToken == null || refreshToken.isBlank()) && body != null) {
            refreshToken = body.get("refreshToken");
        }
        String newAccess = authService.reissue(refreshToken);
        return ResponseEntity.ok(new TokenResponse(newAccess));
    }

    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(
            @CookieValue(name = "refreshToken", required = false) String cookieRefreshToken,
            @RequestBody(required = false) java.util.Map<String, String> body,
            HttpServletResponse response
    ) {
        String refreshToken = cookieRefreshToken;
        if ((refreshToken == null || refreshToken.isBlank()) && body != null) {
            refreshToken = body.get("refreshToken");
        }
        authService.logout(refreshToken, response);
        return ResponseEntity.ok(LogoutResponse.ok());
    }
}
