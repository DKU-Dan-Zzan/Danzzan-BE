package com.danzzan.domain.user.controller;

import com.danzzan.domain.user.phoneverification.dto.request.RequestPhoneVerificationVerifyDto;
import com.danzzan.domain.user.phoneverification.dto.response.ResponsePhoneVerificationCreateDto;
import com.danzzan.domain.user.phoneverification.dto.response.ResponsePhoneVerificationStatusDto;
import com.danzzan.domain.user.phoneverification.service.PhoneVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/user/phone-verifications")
@Tag(name = "전화번호 인증", description = "OCTOMO 기반 전화번호 점유 인증 API")
public class PhoneVerificationController {

    private final PhoneVerificationService phoneVerificationService;

    @PostMapping("/{signup-token}/sessions")
    @Operation(summary = "전화번호 인증 세션 생성", description = "Step2용 인증 세션과 문자 전송용 인증코드를 발급합니다.")
    public ResponseEntity<ResponsePhoneVerificationCreateDto> createSession(
            @PathVariable("signup-token") String signupToken,
            HttpServletRequest request,
            @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor
    ) {
        return ResponseEntity.ok(phoneVerificationService.createSession(
                signupToken,
                extractClientIp(request, forwardedFor)
        ));
    }

    @PostMapping("/sessions/{sessionId}/verify")
    @Operation(summary = "전화번호 인증 검증", description = "사용자 전화번호와 인증코드 조합이 OCTOMO 대표번호 1666-3538로 최근 7분 내 전송되었는지 검증합니다.")
    public ResponseEntity<ResponsePhoneVerificationStatusDto> verifySession(
            @PathVariable String sessionId,
            @Valid @RequestBody RequestPhoneVerificationVerifyDto dto
    ) {
        return ResponseEntity.ok(phoneVerificationService.verifySession(sessionId, dto.getPhoneNumber()));
    }

    @GetMapping("/sessions/{sessionId}")
    @Operation(summary = "전화번호 인증 상태 조회", description = "현재 인증 세션 상태를 조회합니다.")
    public ResponseEntity<ResponsePhoneVerificationStatusDto> getSessionStatus(
            @PathVariable String sessionId
    ) {
        return ResponseEntity.ok(phoneVerificationService.getStatus(sessionId));
    }

    private String extractClientIp(HttpServletRequest request, String forwardedForHeader) {
        if (forwardedForHeader != null && !forwardedForHeader.isBlank()) {
            return forwardedForHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
