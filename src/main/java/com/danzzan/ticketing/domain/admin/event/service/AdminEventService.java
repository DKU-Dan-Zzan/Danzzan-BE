package com.danzzan.ticketing.domain.admin.event.service;

import com.danzzan.ticketing.domain.admin.event.dto.EventListResponseDTO;
import com.danzzan.ticketing.domain.admin.event.dto.EventStatsResponseDTO;

public interface AdminEventService {
    EventListResponseDTO listEvents();
    EventStatsResponseDTO getEventStats(Long eventId);
}
