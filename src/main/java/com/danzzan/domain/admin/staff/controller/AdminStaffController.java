package com.danzzan.domain.admin.staff.controller;

import com.danzzan.domain.admin.staff.dto.StaffMemberResponse;
import com.danzzan.domain.admin.staff.dto.StaffPageResponse;
import com.danzzan.domain.admin.staff.dto.StaffRoleChangeRequest;
import com.danzzan.domain.admin.staff.dto.StaffFilter;
import com.danzzan.domain.admin.staff.exception.StaffManagementException;
import com.danzzan.domain.admin.staff.service.StaffManagementService;
import com.danzzan.global.model.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/staff")
@RequiredArgsConstructor
@PreAuthorize("@userAdminAuthorizationService.hasAdminRole(authentication)")
public class AdminStaffController {

    private final StaffManagementService staffManagementService;

    @GetMapping("/candidates")
    public ResponseEntity<ApiResponse<StaffMemberResponse>> findCandidate(
            Authentication authentication, @RequestParam(required = false) String studentId) {
        return noStore(ApiResponse.success(staffManagementService.findCandidate(actorId(authentication), studentId)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<StaffPageResponse>> findStaff(
            Authentication authentication,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "ALL") StaffFilter filter) {
        return noStore(ApiResponse.success(staffManagementService.findStaff(actorId(authentication), page, size, filter)));
    }

    @PatchMapping("/{userId}/role")
    public ResponseEntity<ApiResponse<StaffMemberResponse>> changeRole(
            Authentication authentication, @PathVariable Long userId, @RequestBody StaffRoleChangeRequest request) {
        return ResponseEntity.ok(ApiResponse.success(staffManagementService.changeRole(actorId(authentication), userId, request)));
    }

    @PostMapping("/{userId}/promote-admin")
    public ResponseEntity<ApiResponse<StaffMemberResponse>> promoteAdmin(
            Authentication authentication, @PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(staffManagementService.promoteAdmin(actorId(authentication), userId)));
    }

    @PostMapping("/{userId}/demote-admin")
    public ResponseEntity<ApiResponse<StaffMemberResponse>> demoteAdmin(
            Authentication authentication, @PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(staffManagementService.demoteAdmin(actorId(authentication), userId)));
    }

    private Long actorId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Long id) return id;
        throw new com.danzzan.global.exception.AdminForbiddenException("관리자 권한이 필요합니다.");
    }

    private <T> ResponseEntity<ApiResponse<T>> noStore(ApiResponse<T> body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
