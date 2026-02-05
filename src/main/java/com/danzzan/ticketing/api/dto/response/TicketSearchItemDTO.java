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
