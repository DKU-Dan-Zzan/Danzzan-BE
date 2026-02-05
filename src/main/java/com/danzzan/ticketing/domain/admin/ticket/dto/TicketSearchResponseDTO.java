package com.danzzan.ticketing.domain.admin.ticket.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketSearchResponseDTO {
    private Long eventId;
    private String studentId;
    private List<TicketSearchItemDTO> results;
}
