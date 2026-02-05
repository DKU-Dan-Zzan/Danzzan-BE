package com.danzzan.ticketing.api.dto.request;

import com.danzzan.ticketing.persistence.entity.SystemType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminLoginRequestDTO {

    @NotNull
    private SystemType system;

    @NotBlank
    private String studentId;

    @NotBlank
    private String password;
}
