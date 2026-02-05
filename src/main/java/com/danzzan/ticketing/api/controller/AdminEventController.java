package com.danzzan.ticketing.api.controller;

import com.danzzan.ticketing.api.common.ApiResponse;
import com.danzzan.ticketing.api.dto.response.EventListResponseDTO;
import com.danzzan.ticketing.api.dto.response.EventStatsResponseDTO;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin")
public interface AdminEventController {

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/events")
    ApiResponse<EventListResponseDTO> listEvents();

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/events/{eventId}/stats")
    ApiResponse<EventStatsResponseDTO> getEventStats(@NotNull @PathVariable Long eventId);
}
