package com.danzzan.domain.timetable.dto.admin.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 공연 수정 요청. 모든 필드는 선택적이며 null이면 해당 항목은 변경하지 않는다.
 * stage는 명시적으로 빈 문자열을 보내면 빈 값으로 저장된다.
 */
@Getter
@Setter
@NoArgsConstructor
public class UpdatePerformanceRequest {

    private Integer artistId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate performanceDate;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime endTime;

    private String stage;
}
