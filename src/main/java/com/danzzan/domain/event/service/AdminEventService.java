package com.danzzan.domain.event.service;

import com.danzzan.domain.event.dto.EventListResponseDTO;
import com.danzzan.domain.event.dto.EventStatsResponseDTO;

public interface AdminEventService {
    EventListResponseDTO listEvents();
    EventStatsResponseDTO getEventStats(Long eventId);
}
