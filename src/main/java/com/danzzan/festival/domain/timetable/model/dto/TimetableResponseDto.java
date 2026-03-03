package com.danzzan.festival.domain.timetable.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@AllArgsConstructor
public class TimetableResponseDto {
    private LocalDate date;
    private List<TimetablePerformanceDto> performances;
}