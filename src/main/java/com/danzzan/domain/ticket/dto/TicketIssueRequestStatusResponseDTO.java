package com.danzzan.domain.ticket.dto;

import com.danzzan.domain.ticket.model.entity.TicketIssueRequestStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "비동기 발급 요청 상태 조회 응답")
public class TicketIssueRequestStatusResponseDTO {

    @Schema(description = "비동기 발급 요청 ID", example = "2d1a4c9f-1fd1-4ef9-b442-c344a1d3950a")
    private String requestId;

    @Schema(description = "이벤트 ID", example = "10")
    private Long eventId;

    @Schema(description = "요청 상태", example = "PROCESSING", allowableValues = {"PROCESSING", "SUCCESS", "FAILED"})
    private TicketIssueRequestStatus status;

    @Schema(description = "실패 코드(FAILED일 때만)", example = "RESERVE_PROCESSING_FAILED")
    private String errorCode;

    @Schema(description = "최근 갱신 시각(epoch ms)", example = "1773486180000")
    private Long updatedAt;
}
