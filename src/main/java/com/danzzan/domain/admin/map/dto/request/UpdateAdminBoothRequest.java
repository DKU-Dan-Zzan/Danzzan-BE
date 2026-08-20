package com.danzzan.domain.admin.map.dto.request;

import com.danzzan.domain.boothmap.model.entity.BoothOperationStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@NoArgsConstructor
public class UpdateAdminBoothRequest {
    @NotNull
    private LocalDate operationDate;

    @NotNull
    private BoothOperationStatus operationStatus;

    private String name;

    private String description;

    private LocalTime startTime;

    private LocalTime endTime;

    @NotNull
    private List<@NotNull LocalDate> operationDates;
}
