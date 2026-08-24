package com.danzzan.domain.timetable.dto.admin.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
public class CreatePerformanceRequest {

    @NotNull(message = "아티스트를 선택해 주세요.")
    private Integer artistId;

    @NotNull(message = "공연 날짜를 입력해 주세요.")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate performanceDate;

    @NotNull(message = "시작 시간을 입력해 주세요.")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @NotNull(message = "종료 시간을 입력해 주세요.")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime endTime;

    private String stage;

    private String stageEn;
}
