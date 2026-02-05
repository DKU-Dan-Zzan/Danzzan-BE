package com.danzzan.ticketing.api.service;

import com.danzzan.ticketing.api.dto.response.EventListResponseDTO;
import com.danzzan.ticketing.api.dto.response.EventStatsResponseDTO;

public interface AdminEventService {
    EventListResponseDTO listEvents();
    EventStatsResponseDTO getEventStats(Long eventId);
}
