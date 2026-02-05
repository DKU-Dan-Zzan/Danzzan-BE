package com.danzzan.ticketing.domain.admin.ticket.dto;

import com.danzzan.ticketing.domain.ticket.model.entity.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketSearchItemDTO {
    private Long ticketId;
    private String studentId;
    private String name;
    private String college;
    private String major;
    private TicketStatus status;
    private String issuedAt;
    private String issuerAdminName;
}
