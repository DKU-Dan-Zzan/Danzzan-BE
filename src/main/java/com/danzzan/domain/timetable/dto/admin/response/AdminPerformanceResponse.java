package com.danzzan.domain.timetable.dto.admin.response;

import com.danzzan.domain.timetable.model.entity.Performance;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Getter
@AllArgsConstructor
public class AdminPerformanceResponse {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private Integer performanceId;
    private LocalDate performanceDate;
    private String startTime;
    private String endTime;
    private String stage;

    private Integer artistId;
    private String artistName;
    private String artistImageUrl;
    private String artistDescription;

    public static AdminPerformanceResponse from(Performance performance) {
        return new AdminPerformanceResponse(
                performance.getId(),
                performance.getPerformanceDate(),
                performance.getStartTime().format(TIME_FORMATTER),
                performance.getEndTime().format(TIME_FORMATTER),
                performance.getStage(),
                performance.getArtist().getId(),
                performance.getArtist().getName(),
                performance.getArtist().getImageUrl(),
                performance.getArtist().getDescription()
        );
    }
}
