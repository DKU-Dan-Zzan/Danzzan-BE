package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.service.model.ClaimResult;

public interface ClaimService {

    ClaimResult claim(String eventId, String userId);
}
