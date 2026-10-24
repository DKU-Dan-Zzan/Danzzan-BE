package com.danzzan.domain.timetable.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.danzzan.infra.image.json.ImageUrlSerializer;
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
    @JsonSerialize(using = ImageUrlSerializer.class)
    private String artistImageUrl;
    private String artistDescription;
}