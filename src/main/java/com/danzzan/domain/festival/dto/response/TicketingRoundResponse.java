package com.danzzan.domain.festival.dto.response;

import com.danzzan.domain.festival.entity.FestivalTicketingRound;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TicketingRoundResponse(
        Long id,
        LocalDateTime ticketingAt,
        int capacity,
        LocalDate performanceDate
) {

    public static TicketingRoundResponse from(FestivalTicketingRound round) {
        return new TicketingRoundResponse(
                round.getId(),
                round.getTicketingAt(),
                round.getCapacity(),
                round.getPerformanceDate()
        );
    }
}
