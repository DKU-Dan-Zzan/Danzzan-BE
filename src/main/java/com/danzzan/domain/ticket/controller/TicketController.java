package com.danzzan.domain.ticket.controller;

import com.danzzan.domain.ticket.dto.ResponseMyTicketListDto;
import com.danzzan.domain.ticket.dto.ResponseReserveTicketDto;
import com.danzzan.domain.ticket.dto.ResponseTicketEventListDto;
import com.danzzan.domain.ticket.dto.TicketRequestResponseDTO;
import com.danzzan.domain.ticket.dto.TicketStatusResponseDTO;
import com.danzzan.domain.ticket.exception.AlreadyReservedException;
import com.danzzan.domain.ticket.exception.EventNotOpenException;
import com.danzzan.domain.ticket.exception.EventSoldOutException;
import com.danzzan.domain.ticket.exception.ReserveProcessingException;
import com.danzzan.domain.ticket.redis.TicketRequestStatus;
import com.danzzan.domain.ticket.service.ClaimService;
import com.danzzan.domain.ticket.service.QueueService;
import com.danzzan.domain.ticket.service.QueueStateService;
import com.danzzan.domain.event.model.entity.TicketingStatus;
import com.danzzan.domain.ticket.service.TicketIssueEnqueueService;
import com.danzzan.domain.ticket.service.TicketService;
import com.danzzan.domain.ticket.service.TicketStatusService;
import com.danzzan.domain.ticket.metrics.TicketingMetrics;
import com.danzzan.domain.ticket.service.model.ClaimResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/tickets")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "사용자 티켓팅", description = "공연 티켓 예매 및 조회 API")
public class TicketController {

    private final TicketService ticketService;
    private final ClaimService claimService;
    private final TicketStatusService ticketStatusService;
    private final QueueService queueService;
    private final QueueStateService queueStateService;
    private final TicketingMetrics ticketingMetrics;
    private final TicketIssueEnqueueService ticketIssueEnqueueService;

    @Value("${app.ticketing.async.reserve.enabled:false}")
    private boolean asyncReserveEnabled;

    @Value("${app.ticketing.async.reserve.processing-ttl-seconds:600}")
    private long processingTtlSeconds;

    @GetMapping("/events")
    @Operation(summary = "이벤트 목록 조회", description = "티켓팅 가능한 공연 목록을 조회합니다. 로그인 불필요.")
    public ResponseEntity<ResponseTicketEventListDto> getTicketingEvents() {
        return ResponseEntity.ok(ticketService.getTicketingEvents());
    }

    @PostMapping("/{eventId}/queue/enter")
    @Operation(summary = "대기열 진입", description = "대기열에 진입합니다. 이미 진입한 경우 현재 상태를 반환합니다.")
    public ResponseEntity<TicketRequestResponseDTO> enterQueue(
            @PathVariable Long eventId,
            Authentication authentication
    ) {
        Long userId = (Long) authentication.getPrincipal();
        String eventIdStr = String.valueOf(eventId);
        String userIdStr = String.valueOf(userId);

        // 이미 터미널 상태면 바로 반환 (Redis 기반)
        TicketRequestStatus currentStatus = ticketStatusService.getStatus(eventIdStr, userIdStr);
        if (currentStatus == TicketRequestStatus.FAILED) {
            ticketStatusService.clearProcessing(eventIdStr, userIdStr);
            currentStatus = TicketRequestStatus.NONE;
        }
        if (isTerminal(currentStatus)) {
            TicketRequestResponseDTO.TicketRequestResponseDTOBuilder builder = TicketRequestResponseDTO.builder()
                    .status(currentStatus);
            if (currentStatus == TicketRequestStatus.PROCESSING) {
                builder.requestId(ticketStatusService.getProcessingRequestId(eventIdStr, userIdStr))
                        .acceptedAt(ticketStatusService.getProcessingAcceptedAt(eventIdStr, userIdStr));
            }
            return ResponseEntity.ok(builder.build());
        }

        // DB 이중 예매 방지 (Redis 초기화 이후에도 보장)
        if (ticketService.hasTicket(userId, eventId)) {
            return ResponseEntity.ok(TicketRequestResponseDTO.builder()
                    .status(TicketRequestStatus.ALREADY)
                    .build());
        }
        // 이벤트 상태를 한 번만 조회해서 CLOSED/READY 모두 처리 (DB 1회)
        TicketingStatus ticketingStatus = ticketService.getTicketingStatus(eventId);
        if (ticketingStatus == TicketingStatus.CLOSED) {
            return ResponseEntity.ok(TicketRequestResponseDTO.builder()
                    .status(TicketRequestStatus.SOLD_OUT)
                    .build());
        }
        // READY(오픈 전) 이벤트: 스케줄러가 OPEN 이벤트만 승격하므로 진입 자체를 차단
        if (ticketingStatus != TicketingStatus.OPEN) {
            return ResponseEntity.ok(TicketRequestResponseDTO.builder()
                    .status(TicketRequestStatus.NONE)
                    .build());
        }

        // 대기열 진입 — Lua로 dedup + INCR seq + ZADD + HSET state=WAITING 원자 처리
        queueService.enterQueue(eventIdStr, userIdStr);

        // 현재 상태 조회 (스케줄러가 이미 READY 승격했을 수도 있음)
        TicketRequestStatus status = ticketStatusService.getStatus(eventIdStr, userIdStr);
        ticketingMetrics.recordQueueEnter(status);
        return ResponseEntity.ok(buildQueueEnterResponse(eventIdStr, userIdStr, status));
    }

    @DeleteMapping("/{eventId}/queue/leave")
    @Operation(summary = "대기열 이탈", description = "WAITING/READY/ACTIVE 상태에서 자발적으로 대기열을 이탈합니다. 슬롯은 즉시 반환됩니다.")
    public ResponseEntity<Void> leaveQueue(
            @PathVariable Long eventId,
            Authentication authentication
    ) {
        Long userId = (Long) authentication.getPrincipal();
        queueStateService.leaveQueue(String.valueOf(eventId), String.valueOf(userId));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{eventId}/queue/status")
    @Operation(summary = "대기열 상태 조회", description = "현재 대기열 상태를 polling합니다.")
    public ResponseEntity<TicketStatusResponseDTO> getQueueStatus(
            @PathVariable Long eventId,
            Authentication authentication
    ) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(buildStatusResponse(String.valueOf(eventId), String.valueOf(userId)));
    }

    @PostMapping("/{eventId}/activate")
    @Operation(summary = "유의사항 화면 진입", description = "READY 상태에서 호출. Lua로 READY→ACTIVE 원자 전환.")
    public ResponseEntity<TicketRequestResponseDTO> activateTicket(
            @PathVariable Long eventId,
            Authentication authentication
    ) {
        Long userId = (Long) authentication.getPrincipal();
        String eventIdStr = String.valueOf(eventId);
        String userIdStr = String.valueOf(userId);

        long result = queueStateService.activateIfReady(eventIdStr, userIdStr);
        if (result == 0) {
            throw new EventNotOpenException("READY 상태가 아닙니다.");
        }
        if (result < 0) {
            throw new EventNotOpenException("입장 허가 시간이 만료되었습니다.");
        }

        return ResponseEntity.ok(TicketRequestResponseDTO.builder()
                .status(TicketRequestStatus.ADMITTED)
                .admissionState(ticketStatusService.getAdmissionState(eventIdStr, userIdStr))
                .build());
    }

    @PostMapping("/{eventId}/reserve")
    @Operation(summary = "티켓 예매", description = "ACTIVE 상태(유의사항 화면)에서 예매완료 버튼 클릭. 재고 차감 후 DB 저장.")
    public ResponseEntity<?> reserveTicket(
            @PathVariable Long eventId,
            Authentication authentication
    ) {
        return asyncReserveEnabled
                ? reserveTicketAsync(eventId, authentication)
                : reserveTicketSync(eventId, authentication);
    }

    private ResponseEntity<ResponseReserveTicketDto> reserveTicketSync(Long eventId, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        String eventIdStr = String.valueOf(eventId);
        String userIdStr = String.valueOf(userId);

        try {
            ClaimResult claimResult = claimService.claim(eventIdStr, userIdStr);

            if (claimResult.status() == TicketRequestStatus.SOLD_OUT) {
                throw new EventSoldOutException();
            }
            if (claimResult.status() == TicketRequestStatus.ALREADY) {
                throw new AlreadyReservedException();
            }

            ResponseReserveTicketDto response;
            try {
                response = ticketService.persistAndBuildResponse(userId, eventId, claimResult.remaining());
            } catch (Exception e) {
                claimService.rollback(eventIdStr, userIdStr);
                throw e;
            }

            markDoneAfterPersistence(eventIdStr, userIdStr);
            return ResponseEntity.ok(response);

        } finally {
            // 성공/실패 무관하게 active ZSet 제거 → 슬롯 반환
            queueStateService.releaseActive(eventIdStr, userIdStr);
        }
    }

    private ResponseEntity<TicketRequestResponseDTO> reserveTicketAsync(Long eventId, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        String eventIdStr = String.valueOf(eventId);
        String userIdStr = String.valueOf(userId);

        try {
            TicketRequestStatus currentStatus = ticketStatusService.getStatus(eventIdStr, userIdStr);
            if (currentStatus == TicketRequestStatus.PROCESSING) {
                return processingAcceptedResponse(eventId, userId, eventIdStr, userIdStr);
            }

            ClaimResult claimResult = claimService.claim(eventIdStr, userIdStr);
            if (claimResult.status() == TicketRequestStatus.SOLD_OUT) {
                throw new EventSoldOutException();
            }
            if (claimResult.status() == TicketRequestStatus.ALREADY) {
                throw new AlreadyReservedException();
            }

            String requestId = UUID.randomUUID().toString();
            long acceptedAt = System.currentTimeMillis();
            ticketStatusService.setProcessing(eventIdStr, userIdStr, requestId, acceptedAt, processingTtlSeconds);

            try {
                Long seq = ticketStatusService.getMySequence(eventIdStr, userIdStr);
                requestId = ticketIssueEnqueueService.enqueueIssueRequest(
                        eventId,
                        userId,
                        requestId,
                        claimResult.remaining(),
                        seq,
                        acceptedAt
                );
            } catch (Exception e) {
                ticketStatusService.clearProcessing(eventIdStr, userIdStr);
                claimService.rollback(eventIdStr, userIdStr);
                throw new ReserveProcessingException();
            }

            return ResponseEntity.accepted().body(TicketRequestResponseDTO.builder()
                    .status(TicketRequestStatus.PROCESSING)
                    .requestId(requestId)
                    .acceptedAt(acceptedAt)
                    .build());

        } finally {
            queueStateService.releaseActive(eventIdStr, userIdStr);
        }
    }

    private ResponseEntity<TicketRequestResponseDTO> processingAcceptedResponse(
            Long eventId,
            Long userId,
            String eventIdStr,
            String userIdStr
    ) {
        String requestId = ticketStatusService.getProcessingRequestId(eventIdStr, userIdStr);
        Long acceptedAt = ticketStatusService.getProcessingAcceptedAt(eventIdStr, userIdStr);

        if (requestId == null || acceptedAt == null) {
            TicketIssueEnqueueService.InFlightProcessingRequest inFlight =
                    ticketIssueEnqueueService.findProcessingRequest(eventId, userId).orElse(null);
            if (inFlight != null) {
                if (requestId == null) {
                    requestId = inFlight.requestId();
                }
                if (acceptedAt == null) {
                    acceptedAt = inFlight.acceptedAt();
                }
            }
        }

        return ResponseEntity.accepted().body(TicketRequestResponseDTO.builder()
                .status(TicketRequestStatus.PROCESSING)
                .requestId(requestId)
                .acceptedAt(acceptedAt)
                .build());
    }

    @GetMapping("/me")
    @Operation(summary = "내 티켓 조회", description = "내가 예매한 티켓 목록을 조회합니다.")
    public ResponseEntity<ResponseMyTicketListDto> getMyTickets(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ticketService.getMyTickets(userId));
    }

    private TicketStatusResponseDTO buildStatusResponse(String eventId, String userId) {
        TicketRequestStatus status = ticketStatusService.getStatus(eventId, userId);
        boolean hideAdmissionDetails = status == TicketRequestStatus.PROCESSING || status == TicketRequestStatus.FAILED;
        Long aheadCount = ticketStatusService.getAheadCount(eventId, userId);
        return TicketStatusResponseDTO.builder()
                .status(status)
                .queuePosition(status == TicketRequestStatus.WAITING
                        ? ticketStatusService.getQueuePosition(eventId, userId)
                        : null)
                .mySequence(ticketStatusService.getMySequence(eventId, userId))
                .aheadCount(aheadCount)
                .estimatedWaitSeconds(ticketStatusService.getEstimatedWaitSeconds(aheadCount))
                .readyUntil(hideAdmissionDetails ? null : ticketStatusService.getReadyUntil(eventId, userId))
                .admissionState(hideAdmissionDetails ? null : ticketStatusService.getAdmissionState(eventId, userId))
                .build();
    }

    private TicketRequestResponseDTO buildQueueEnterResponse(String eventId, String userId, TicketRequestStatus status) {
        Long aheadCount = ticketStatusService.getAheadCount(eventId, userId);
        return TicketRequestResponseDTO.builder()
                .status(status)
                .queuePosition(status == TicketRequestStatus.WAITING
                        ? ticketStatusService.getQueuePosition(eventId, userId)
                        : null)
                .mySequence(ticketStatusService.getMySequence(eventId, userId))
                .aheadCount(aheadCount)
                .estimatedWaitSeconds(ticketStatusService.getEstimatedWaitSeconds(aheadCount))
                .readyUntil(ticketStatusService.getReadyUntil(eventId, userId))
                .admissionState(ticketStatusService.getAdmissionState(eventId, userId))
                .build();
    }

    private void markDoneAfterPersistence(String eventId, String userId) {
        try {
            queueStateService.markDone(eventId, userId);
        } catch (Exception e) {
            log.error("markDone 실패, 예매는 이미 DB에 저장됨 eventId={} userId={}", eventId, userId, e);
        }
    }

    private boolean isTerminal(TicketRequestStatus status) {
        return status == TicketRequestStatus.SUCCESS
                || status == TicketRequestStatus.SOLD_OUT
                || status == TicketRequestStatus.ALREADY
                || status == TicketRequestStatus.PROCESSING;
    }
}
