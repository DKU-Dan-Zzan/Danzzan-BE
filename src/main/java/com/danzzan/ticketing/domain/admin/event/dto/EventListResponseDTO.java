package com.danzzan.ticketing.domain.admin.event.dto;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "팔찌 배부 대상 공연 목록 응답")
public class EventListResponseDTO {
    @Schema(description = "팔찌 배부 대상 공연 목록")
    private List<EventSummaryDTO> events;
}
