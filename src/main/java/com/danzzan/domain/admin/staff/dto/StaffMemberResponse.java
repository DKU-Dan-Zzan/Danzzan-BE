package com.danzzan.domain.admin.staff.dto;

import com.danzzan.domain.user.model.entity.User;
import com.danzzan.domain.user.model.entity.ManagerPermission;
import java.util.List;

public record StaffMemberResponse(
        Long id,
        String studentId,
        String name,
        String college,
        String major,
        String role,
        List<String> permissions
) {
    public static StaffMemberResponse from(User user) {
        return new StaffMemberResponse(
                user.getId(), user.getStudentId(), user.getName(), user.getCollege(), user.getMajor(),
                switch (user.getRole()) {
                    case ROLE_USER -> "USER";
                    case ROLE_MANAGER -> "MANAGER";
                    case ROLE_ADMIN -> "ADMIN";
                }, user.getManagerPermissions().stream().map(ManagerPermission::name).toList()
        );
    }
}
