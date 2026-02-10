package com.danzzan.ticketing.domain.admin.event.dto;

import com.danzzan.ticketing.domain.event.model.entity.TicketingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "공연 요약 정보")
public class EventSummaryDTO {
    @Schema(description = "공연 ID (festival_events.id)", example = "2")
    private Long eventId;
    @Schema(description = "공연/운영 제목 (festival_events.title)", example = "5월 13일 공연 팔찌 배부")
    private String title;
    @Schema(description = "화면 표기용 DAY 라벨", example = "DAY 2")
    private String dayLabel;
    @Schema(description = "공연 날짜 (YYYY-MM-DD)", example = "2026-05-13")
    private String eventDate;
    @Schema(description = "티켓팅 상태", example = "OPEN")
    private TicketingStatus ticketingStatus;
    @Schema(description = "정원 (festival_events.total_capacity)", example = "5000")
    private int totalCapacity;
}
