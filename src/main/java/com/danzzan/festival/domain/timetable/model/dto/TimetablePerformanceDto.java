package com.danzzan.festival.domain.timetable.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TimetablePerformanceDto {
    private Integer performanceId;
    private String startTime;
    private String endTime;
    private String stage;

    private Integer artistId;
    private String artistName;
    private String artistImageUrl;
    private String artistDescription;
}