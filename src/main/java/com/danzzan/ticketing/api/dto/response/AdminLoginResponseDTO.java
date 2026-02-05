package com.danzzan.ticketing.api.dto.response;

import com.danzzan.ticketing.persistence.entity.SystemType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminLoginResponseDTO {
    private String accessToken;
    private AdminInfoDTO admin;
    private SystemType system;
}
