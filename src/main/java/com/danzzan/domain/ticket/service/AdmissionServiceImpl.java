package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.redis.TicketRequestStatus;
import org.springframework.stereotype.Service;

@Service
public class AdmissionServiceImpl implements AdmissionService {

    @Override
    public TicketRequestStatus admit(String eventId, String userId) {
        return TicketRequestStatus.ADMITTED;
    }
}
