package com.danzzan.domain.festival.dto.response;

import com.danzzan.domain.festival.entity.FestivalTicketingRound;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @param locked 티켓팅이 이미 시작됐거나 티켓이 나간 회차. 고치거나 지울 수 없어
 *               관리자 화면에서 입력을 잠근다.
 */
public record TicketingRoundResponse(
        Long id,
        LocalDateTime ticketingAt,
        int capacity,
        LocalDate performanceDate,
        boolean locked
) {

    public static TicketingRoundResponse from(FestivalTicketingRound round, boolean locked) {
        return new TicketingRoundResponse(
                round.getId(),
                round.getTicketingAt(),
                round.getCapacity(),
                round.getPerformanceDate(),
                locked
        );
    }
}
