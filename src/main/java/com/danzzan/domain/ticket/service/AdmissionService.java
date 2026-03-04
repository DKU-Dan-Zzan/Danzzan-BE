package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.redis.TicketRequestStatus;

public interface AdmissionService {

    TicketRequestStatus admit(String eventId, String userId);
}
