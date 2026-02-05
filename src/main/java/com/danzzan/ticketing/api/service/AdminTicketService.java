package com.danzzan.ticketing.api.service;

import com.danzzan.ticketing.api.dto.response.IssueTicketResponseDTO;
import com.danzzan.ticketing.api.dto.response.TicketSearchResponseDTO;

public interface AdminTicketService {
    TicketSearchResponseDTO searchTicketByStudentId(Long eventId, String studentId);
    IssueTicketResponseDTO issueTicket(Long eventId, Long ticketId, String note);
}
