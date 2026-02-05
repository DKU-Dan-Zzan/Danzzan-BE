package com.danzzan.ticketing.api.dto.response;

import com.danzzan.ticketing.persistence.entity.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueTicketResponseDTO {
    private Long ticketId;
    private TicketStatus status;
    private String issuedAt;
    private Long issuerAdminId;
    private String issuerAdminName;
}
