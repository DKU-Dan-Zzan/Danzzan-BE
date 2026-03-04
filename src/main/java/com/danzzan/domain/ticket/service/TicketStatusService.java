package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.redis.TicketRequestStatus;

public interface TicketStatusService {

    TicketRequestStatus getStatus(String eventId, String userId);
}
