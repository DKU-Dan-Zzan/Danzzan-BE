package com.danzzan.domain.ticket.dto;

import com.danzzan.domain.ticket.redis.QueueUserState;
import com.danzzan.domain.ticket.redis.TicketRequestStatus;
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
@Schema(description = "티켓 요청(선착순) 응답")
public class TicketRequestResponseDTO {

    @Schema(description = "요청 결과 상태", example = "SUCCESS")
    private TicketRequestStatus status;

    @Schema(description = "남은 재고(선택)", example = "42", nullable = true)
    private Long remaining;

    @Schema(description = "대기열 순번(대기 중일 때만, 1-indexed) — WAITING 외 상태에서는 필드 자체가 응답에서 생략됨", example = "142")
    private Long queuePosition;

    @Schema(description = "발급된 고유 순번(seq)", example = "142")
    private Long mySequence;

    @Schema(description = "내 앞에 남아 있는 WAITING 인원 수", example = "141")
    private Long aheadCount;

    @Schema(description = "예상 대기 시간(초)", example = "180")
    private Long estimatedWaitSeconds;

    @Schema(description = "READY 상태 permit 만료 시각(epoch ms)", example = "1773486180000")
    private Long readyUntil;

    @Schema(description = "ADMITTED 내부 상태(READY 또는 ACTIVE)", example = "ACTIVE")
    private QueueUserState admissionState;
}
