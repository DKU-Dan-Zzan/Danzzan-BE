package com.danzzan.ticketing.domain.admin.auth.dto;

import com.danzzan.ticketing.domain.user.model.entity.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminInfoDTO {
    private Long id;
    private String name;
    private String studentId;
    private UserRole role;
}
