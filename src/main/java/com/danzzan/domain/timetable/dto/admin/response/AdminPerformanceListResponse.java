package com.danzzan.domain.timetable.dto.admin.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@AllArgsConstructor
public class AdminPerformanceListResponse {
    private LocalDate date;
    private List<AdminPerformanceResponse> performances;
}
