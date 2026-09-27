package com.danzzan.domain.festival.dto.response;

import com.danzzan.domain.festival.entity.FestivalTicketingRound;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @param locked             티켓팅이 이미 시작된 회차. 내용을 고칠 수 없다.
 * @param issuedTicketCount  이 회차로 나간 티켓 수. 0 보다 크면 삭제할 때 확인을 받아야 한다.
 */
public record TicketingRoundResponse(
        Long id,
        LocalDateTime ticketingAt,
        int capacity,
        LocalDate performanceDate,
        boolean locked,
        long issuedTicketCount
) {

    public static TicketingRoundResponse from(FestivalTicketingRound round, boolean locked, long issuedTicketCount) {
        return new TicketingRoundResponse(
                round.getId(),
                round.getTicketingAt(),
                round.getCapacity(),
                round.getPerformanceDate(),
                locked,
                issuedTicketCount
        );
    }
}
