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
public class AdminMeResponseDTO {
    private Long adminId;
    private String adminName;
    private String studentId;
    private UserRole role;
}
