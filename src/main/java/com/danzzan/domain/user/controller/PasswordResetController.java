package com.danzzan.domain.user.controller;

import com.danzzan.domain.user.passwordreset.dto.request.RequestPasswordResetDto;
import com.danzzan.domain.user.passwordreset.dto.request.RequestPasswordResetRequestDto;
import com.danzzan.domain.user.passwordreset.dto.request.RequestPasswordResetVerifyDto;
import com.danzzan.domain.user.passwordreset.dto.response.ResponsePasswordResetRequestDto;
import com.danzzan.domain.user.passwordreset.dto.response.ResponsePasswordResetVerifyDto;
import com.danzzan.domain.user.passwordreset.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/user/password/reset")
@Tag(name = "비밀번호 재설정", description = "인증코드 발송/검증/비밀번호 재설정 API")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    @PostMapping("/request")
    @Operation(summary = "비밀번호 재설정 요청", description = "학번 기준으로 인증코드를 발송합니다.")
    public ResponseEntity<ResponsePasswordResetRequestDto> requestReset(
            @Valid @RequestBody RequestPasswordResetRequestDto dto,
            HttpServletRequest request,
            @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor
    ) {
        ResponsePasswordResetRequestDto response = passwordResetService.requestReset(
                dto,
                extractClientIp(request, forwardedFor)
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify")
    @Operation(summary = "인증코드 검증", description = "6자리 인증코드를 검증하고 verificationToken을 발급합니다.")
    public ResponseEntity<ResponsePasswordResetVerifyDto> verifyCode(
            @Valid @RequestBody RequestPasswordResetVerifyDto dto
    ) {
        ResponsePasswordResetVerifyDto response = passwordResetService.verifyCode(dto);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Operation(summary = "비밀번호 재설정", description = "verificationToken 검증 후 새 비밀번호로 변경합니다.")
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody RequestPasswordResetDto dto
    ) {
        passwordResetService.resetPassword(dto);
        return ResponseEntity.ok().build();
    }

    private String extractClientIp(HttpServletRequest request, String forwardedForHeader) {
        if (forwardedForHeader != null && !forwardedForHeader.isBlank()) {
            return forwardedForHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
