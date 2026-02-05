package com.danzzan.ticketing.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventStatsResponseDTO {
    private Long eventId;
    private String title;
    private String eventDate;
    private int totalCapacity;
    private long totalTickets;
    private long ticketsConfirmed;
    private long ticketsIssued;
    private double issueRate;
    private int remainingCapacity;
}
