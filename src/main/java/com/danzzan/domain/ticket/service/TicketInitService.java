package com.danzzan.domain.ticket.service;

import com.danzzan.domain.event.model.entity.TicketingStatus;
import com.danzzan.domain.ticket.dto.AdminTicketInitResponseDTO;

import java.util.List;

public interface TicketInitService {

    AdminTicketInitResponseDTO initStock(String eventId, Long stock);

    /**
     * stock 키가 없는 경우에만 재고를 복원합니다. (SET NX)
     * 이미 키가 존재하면(정상 0 포함) 덮어쓰지 않습니다.
     *
     * @return true = 복원 성공, false = 이미 존재해서 건너뜀
     */
    boolean restoreStockIfMissing(String eventId, long stock);

    /**
     * 이미 발급된 티켓 유저를 Redis userKey로 동기화합니다.
     *
     * @return 동기화한 userKey 수
     */
    long syncIssuedUsers(String eventId, List<Long> userIds);

    /**
     * 이벤트 상태를 Redis에 기록합니다.
     */
    void setEventStatus(String eventId, TicketingStatus status);
}
