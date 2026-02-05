package com.danzzan.ticketing.domain.admin.event.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventListResponseDTO {
    private List<EventSummaryDTO> events;
}
