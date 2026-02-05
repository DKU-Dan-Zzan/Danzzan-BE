package com.danzzan.ticketing.api.dto.response;

import com.danzzan.ticketing.persistence.entity.UserRole;
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
