package com.danzzan.domain.admin.map.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@NoArgsConstructor
public class UpsertAdminPubOperationRequest {
    @NotNull
    private LocalDate operationDate;

    @NotNull
    private LocalTime startTime;

    @NotNull
    private LocalTime endTime;
}
