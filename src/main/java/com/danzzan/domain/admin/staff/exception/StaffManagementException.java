package com.danzzan.domain.admin.staff.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class StaffManagementException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    private StaffManagementException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static StaffManagementException invalidStudentId() { return new StaffManagementException("INVALID_STUDENT_ID", HttpStatus.BAD_REQUEST, "학번은 공백을 제외하고 1~255자여야 합니다."); }
    public static StaffManagementException invalidRole() { return new StaffManagementException("INVALID_STAFF_ROLE", HttpStatus.BAD_REQUEST, "매니저 역할은 MANAGER 또는 USER여야 합니다."); }
    public static StaffManagementException invalidTargetId() { return new StaffManagementException("INVALID_STAFF_TARGET_ID", HttpStatus.BAD_REQUEST, "대상 회원 ID가 올바르지 않습니다."); }
    public static StaffManagementException invalidPage() { return new StaffManagementException("INVALID_PAGE_REQUEST", HttpStatus.BAD_REQUEST, "페이지 요청 값이 올바르지 않습니다."); }
    public static StaffManagementException invalidFilter() { return new StaffManagementException("INVALID_STAFF_FILTER", HttpStatus.BAD_REQUEST, "관리자 목록 필터 값이 올바르지 않습니다."); }
    public static StaffManagementException targetNotFound() { return new StaffManagementException("STAFF_TARGET_NOT_FOUND", HttpStatus.NOT_FOUND, "활성 회원을 찾을 수 없습니다."); }
    public static StaffManagementException conflict() { return new StaffManagementException("STAFF_ROLE_CONFLICT", HttpStatus.CONFLICT, "현재 상태에서는 매니저 권한을 변경할 수 없습니다."); }
    public static StaffManagementException disabled() { return new StaffManagementException("STAFF_MANAGEMENT_DISABLED", HttpStatus.SERVICE_UNAVAILABLE, "매니저 관리 기능이 비활성화되어 있습니다."); }
}
