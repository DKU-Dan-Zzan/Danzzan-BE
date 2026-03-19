package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.dto.AdminTicketInitResponseDTO;

public interface TicketInitService {

    AdminTicketInitResponseDTO initStock(String eventId, Long stock);

    /**
     * stock 키가 없는 경우에만 재고를 복원합니다. (SET NX)
     * 이미 키가 존재하면(정상 0 포함) 덮어쓰지 않습니다.
     *
     * @return true = 복원 성공, false = 이미 존재해서 건너뜀
     */
    boolean restoreStockIfMissing(String eventId, long stock);
}
