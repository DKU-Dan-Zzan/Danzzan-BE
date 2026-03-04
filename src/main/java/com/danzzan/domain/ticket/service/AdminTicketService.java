package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.dto.IssueTicketResponseDTO;
import com.danzzan.domain.ticket.dto.TicketSearchResponseDTO;

public interface AdminTicketService {
    TicketSearchResponseDTO searchTicketByStudentId(Long eventId, String studentId);
    IssueTicketResponseDTO issueTicket(Long eventId, Long ticketId, String note);
    IssueTicketResponseDTO cancelIssueTicket(Long eventId, Long ticketId);
}
