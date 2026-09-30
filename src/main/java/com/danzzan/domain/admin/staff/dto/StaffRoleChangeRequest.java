package com.danzzan.domain.admin.staff.dto;

import java.util.List;

public record StaffRoleChangeRequest(String role, List<String> permissions) {
    public StaffRoleChangeRequest(String role) {
        this(role, null);
    }
}
