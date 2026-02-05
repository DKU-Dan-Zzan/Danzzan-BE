package com.danzzan.ticketing.domain.admin.event.dto;

import com.danzzan.ticketing.domain.event.model.entity.TicketingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventSummaryDTO {
    private Long eventId;
    private String title;
    private String dayLabel;
    private String eventDate;
    private TicketingStatus ticketingStatus;
    private int totalCapacity;
}
