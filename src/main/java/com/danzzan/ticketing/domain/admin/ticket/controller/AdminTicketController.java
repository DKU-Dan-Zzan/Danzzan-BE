package com.danzzan.ticketing.domain.admin.ticket.controller;

import com.danzzan.ticketing.global.model.ApiResponse;
import com.danzzan.ticketing.domain.admin.ticket.dto.IssueTicketRequestDTO;
import com.danzzan.ticketing.domain.admin.ticket.dto.IssueTicketResponseDTO;
import com.danzzan.ticketing.domain.admin.ticket.dto.TicketSearchResponseDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin")
public interface AdminTicketController {

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/events/{eventId}/tickets/search")
    ApiResponse<TicketSearchResponseDTO> searchTickets(
            @NotNull @PathVariable Long eventId,
            @NotBlank @RequestParam String studentId
    );

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/events/{eventId}/tickets/{ticketId}/issue")
    ApiResponse<IssueTicketResponseDTO> issueTicket(
            @NotNull @PathVariable Long eventId,
            @NotNull @PathVariable Long ticketId,
            @Valid @RequestBody(required = false) IssueTicketRequestDTO request
    );
}
