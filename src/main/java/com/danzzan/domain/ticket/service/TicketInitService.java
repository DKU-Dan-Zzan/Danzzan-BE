package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.dto.AdminTicketInitResponseDTO;

public interface TicketInitService {

    AdminTicketInitResponseDTO initStock(String eventId, Long stock);
}
