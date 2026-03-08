package com.danzzan.domain.user.controller;

import com.danzzan.domain.user.passwordreset.dto.request.RequestPasswordResetDto;
import com.danzzan.domain.user.passwordreset.dto.request.RequestPasswordResetRequestDto;
import com.danzzan.domain.user.passwordreset.dto.request.RequestPasswordResetVerifyDto;
import com.danzzan.domain.user.passwordreset.dto.response.PasswordResetErrorResponse;
import com.danzzan.domain.user.passwordreset.dto.response.ResponsePasswordResetRequestDto;
import com.danzzan.domain.user.passwordreset.dto.response.ResponsePasswordResetVerifyDto;
import com.danzzan.domain.user.passwordreset.service.PasswordResetService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "요청 접수 성공"),
            @ApiResponse(
                    responseCode = "429",
                    description = "요청 제한 초과(쿨다운/레이트리밋)",
                    content = @Content(
                            schema = @Schema(implementation = PasswordResetErrorResponse.class),
                            examples = @ExampleObject(
                                    value = "{\"error\":\"요청 횟수 제한을 초과했습니다.\",\"errorCode\":\"PASSWORD_RESET_RATE_LIMITED\"}"
                            )
                    )
            )
    })
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
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "인증코드 검증 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "코드 불일치 또는 잘못된 요청",
                    content = @Content(
                            schema = @Schema(implementation = PasswordResetErrorResponse.class),
                            examples = @ExampleObject(
                                    value = "{\"error\":\"인증코드가 일치하지 않습니다.\",\"errorCode\":\"PASSWORD_RESET_CODE_MISMATCH\"}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "요청 없음",
                    content = @Content(
                            schema = @Schema(implementation = PasswordResetErrorResponse.class),
                            examples = @ExampleObject(
                                    value = "{\"error\":\"비밀번호 재설정 요청을 찾을 수 없습니다.\",\"errorCode\":\"PASSWORD_RESET_REQUEST_NOT_FOUND\"}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "410",
                    description = "코드 만료",
                    content = @Content(
                            schema = @Schema(implementation = PasswordResetErrorResponse.class),
                            examples = @ExampleObject(
                                    value = "{\"error\":\"인증코드가 만료되었습니다.\",\"errorCode\":\"PASSWORD_RESET_CODE_EXPIRED\"}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "시도 횟수 초과",
                    content = @Content(
                            schema = @Schema(implementation = PasswordResetErrorResponse.class),
                            examples = @ExampleObject(
                                    value = "{\"error\":\"인증 시도 횟수를 초과했습니다.\",\"errorCode\":\"PASSWORD_RESET_TOO_MANY_ATTEMPTS\"}"
                            )
                    )
            )
    })
    public ResponseEntity<ResponsePasswordResetVerifyDto> verifyCode(
            @Valid @RequestBody RequestPasswordResetVerifyDto dto
    ) {
        ResponsePasswordResetVerifyDto response = passwordResetService.verifyCode(dto);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Operation(summary = "비밀번호 재설정", description = "verificationToken 검증 후 새 비밀번호로 변경합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "비밀번호 재설정 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "미검증 토큰 또는 유효하지 않은 토큰",
                    content = @Content(
                            schema = @Schema(implementation = PasswordResetErrorResponse.class),
                            examples = @ExampleObject(
                                    value = "{\"error\":\"유효하지 않은 verificationToken입니다.\",\"errorCode\":\"PASSWORD_RESET_INVALID_VERIFICATION_TOKEN\"}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "요청 없음 또는 사용자 없음",
                    content = @Content(
                            schema = @Schema(implementation = PasswordResetErrorResponse.class),
                            examples = @ExampleObject(
                                    value = "{\"error\":\"사용자를 찾을 수 없습니다.\",\"errorCode\":\"PASSWORD_RESET_USER_NOT_FOUND\"}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 사용된 토큰",
                    content = @Content(
                            schema = @Schema(implementation = PasswordResetErrorResponse.class),
                            examples = @ExampleObject(
                                    value = "{\"error\":\"이미 사용된 verificationToken입니다.\",\"errorCode\":\"PASSWORD_RESET_TOKEN_ALREADY_CONSUMED\"}"
                            )
                    )
            )
    })
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
