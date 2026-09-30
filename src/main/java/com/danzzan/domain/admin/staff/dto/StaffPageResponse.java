package com.danzzan.domain.admin.staff.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record StaffPageResponse(
        List<StaffMemberResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean managementEnabled
) {
    public static StaffPageResponse from(Page<StaffMemberResponse> page, boolean managementEnabled) {
        return new StaffPageResponse(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), managementEnabled);
    }
}
