package com.danzzan.global.exception;

import com.danzzan.domain.event.exception.EventNotFoundException;
import com.danzzan.domain.ticket.exception.AlreadyReservedException;
import com.danzzan.domain.ticket.exception.EventNotOpenException;
import com.danzzan.domain.ticket.exception.EventSoldOutException;
import com.danzzan.domain.ticket.exception.TicketAlreadyIssuedException;
import com.danzzan.domain.ticket.exception.TicketEventMismatchException;
import com.danzzan.domain.ticket.exception.TicketNotFoundException;
import com.danzzan.domain.ticket.exception.TicketNotIssuedException;
import com.danzzan.domain.user.exception.AlreadyStudentIdException;
import com.danzzan.domain.user.exception.UserNotFoundException;
import com.danzzan.domain.user.exception.WrongPasswordException;
import com.danzzan.global.exception.AdminAuthenticationException;
import com.danzzan.global.exception.AdminForbiddenException;
import com.danzzan.global.model.ApiError;
import com.danzzan.global.model.ApiResponse;
import com.danzzan.infra.dku.exception.DkuFailedCrawlingException;
import com.danzzan.infra.dku.exception.DkuFailedLoginException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException e, HttpServletRequest request) {
        if (isTicketingApi(request)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(e.getMessage(), 404));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> handleIllegalState(IllegalStateException e, HttpServletRequest request) {
        if (isTicketingApi(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        }
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(e.getMessage(), 400));
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ErrorResponse> handleAuthException(AuthException e) {
        return ResponseEntity
                .status(e.getStatus())
                .body(ErrorResponse.of(e.getMessage(), e.getStatus().value()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        if (isTicketingApi(request)) {
            String message = e.getBindingResult().getFieldErrors().stream()
                    .findFirst()
                    .map(error -> error.getDefaultMessage())
                    .orElse("요청 값이 올바르지 않습니다.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", message));
        }

        List<ErrorResponse.FieldError> errors = e.getBindingResult().getFieldErrors().stream()
                .map(err -> new ErrorResponse.FieldError(err.getField(), err.getDefaultMessage()))
                .collect(Collectors.toList());
        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.of("요청 값이 올바르지 않습니다.", 400, errors));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> handleTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        String param = e.getName();
        String msg = String.format("요청 파라미터 '%s' 형식이 올바르지 않습니다.", param);

        if (isTicketingApi(request)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", msg));
        }

        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.of(msg, 400));
    }

    @ExceptionHandler(AlreadyStudentIdException.class)
    public ResponseEntity<Map<String, String>> handleAlreadyStudentId(AlreadyStudentIdException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleUserNotFound(UserNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(WrongPasswordException.class)
    public ResponseEntity<Map<String, String>> handleWrongPassword(WrongPasswordException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(TicketNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleTicketNotFound(TicketNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("TICKET_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(EventNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleEventNotFound(EventNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("EVENT_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(TicketEventMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTicketEventMismatch(TicketEventMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("EVENT_MISMATCH", e.getMessage()));
    }

    @ExceptionHandler(TicketAlreadyIssuedException.class)
    public ResponseEntity<ApiResponse<Void>> handleTicketAlreadyIssued(TicketAlreadyIssuedException e) {
        ApiError error = ApiError.builder()
                .error("ALREADY_ISSUED")
                .message(e.getMessage())
                .ticketId(e.getTicketId())
                .issuedAt(e.getIssuedAt())
                .issuerAdminName(e.getIssuerAdminName())
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .data(null)
                        .error(error)
                        .build());
    }

    @ExceptionHandler(TicketNotIssuedException.class)
    public ResponseEntity<ApiResponse<Void>> handleTicketNotIssued(TicketNotIssuedException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("NOT_ISSUED", e.getMessage()));
    }

    @ExceptionHandler(EventNotOpenException.class)
    public ResponseEntity<Map<String, String>> handleEventNotOpen(EventNotOpenException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(EventSoldOutException.class)
    public ResponseEntity<Map<String, String>> handleEventSoldOut(EventSoldOutException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(AlreadyReservedException.class)
    public ResponseEntity<Map<String, String>> handleAlreadyReserved(AlreadyReservedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(AdminAuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAdminAuthentication(AdminAuthenticationException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("UNAUTHORIZED", e.getMessage()));
    }

    @ExceptionHandler(AdminForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleAdminForbidden(AdminForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("FORBIDDEN", e.getMessage()));
    }

    @ExceptionHandler(DkuFailedLoginException.class)
    public ResponseEntity<Map<String, String>> handleDkuFailedLogin(DkuFailedLoginException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(DkuFailedCrawlingException.class)
    public ResponseEntity<Map<String, String>> handleDkuFailedCrawling(DkuFailedCrawlingException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", e.getMessage()));
    }

    private boolean isTicketingApi(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/user")
                || path.startsWith("/tickets")
                || path.startsWith("/api/admin/events")
                || path.startsWith("/api/admin/ticket");
    }
}
