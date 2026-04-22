package com.danzzan.domain.ticket.controller;

import com.danzzan.domain.ticket.dto.ResponseMyTicketListDto;
import com.danzzan.domain.ticket.dto.ResponseReserveTicketDto;
import com.danzzan.domain.ticket.dto.ResponseTicketEventListDto;
import com.danzzan.domain.ticket.dto.TicketIssueRequestStatusResponseDTO;
import com.danzzan.domain.ticket.dto.TicketRequestResponseDTO;
import com.danzzan.domain.ticket.dto.TicketStatusResponseDTO;
import com.danzzan.domain.ticket.model.entity.TicketIssueRequestStatus;
import com.danzzan.domain.ticket.exception.AlreadyReservedException;
import com.danzzan.domain.ticket.exception.EventNotOpenException;
import com.danzzan.domain.ticket.exception.EventSoldOutException;
import com.danzzan.domain.ticket.exception.ReserveProcessingException;
import com.danzzan.domain.ticket.redis.QueueUserState;
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
import com.danzzan.domain.ticket.service.model.QueueStatusSnapshot;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
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

    @Value("${app.ticketing.async.reserve.shadow-publish.enabled:false}")
    private boolean asyncReserveShadowPublishEnabled;

    @Value("${app.ticketing.async.reserve.rollout-percent:0}")
    private int asyncReserveRolloutPercent;

    @Value("${app.ticketing.async.reserve.allowed-event-ids:}")
    private String asyncReserveAllowedEventIds;

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
        // 이벤트 상태를 Redis 우선 조회해 CLOSED/READY 모두 처리
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

        // 단일 Pipeline 스냅샷으로 상태 조회 (스케줄러가 이미 READY 승격했을 수도 있음)
        QueueStatusSnapshot snap = ticketStatusService.fetchSnapshot(eventIdStr, userIdStr);
        TicketRequestStatus status = resolveStatus(snap);
        ticketingMetrics.recordQueueEnter(status);
        return ResponseEntity.ok(buildQueueEnterResponseFromSnapshot(snap, status));
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

    @GetMapping("/{eventId}/requests/{requestId}")
    @Operation(summary = "비동기 발급 요청 상태 조회", description = "requestId 기준으로 비동기 발급 요청의 현재 상태를 조회합니다.")
    public ResponseEntity<TicketIssueRequestStatusResponseDTO> getRequestStatus(
            @PathVariable Long eventId,
            @PathVariable String requestId,
            Authentication authentication
    ) {
        Long userId = (Long) authentication.getPrincipal();
        return ticketIssueEnqueueService.findRequestStatus(eventId, userId, requestId)
                .map(snapshot -> ResponseEntity.ok(TicketIssueRequestStatusResponseDTO.builder()
                        .requestId(snapshot.requestId())
                        .eventId(snapshot.eventId())
                        .status(snapshot.status())
                        .errorCode(snapshot.status() == TicketIssueRequestStatus.FAILED ? snapshot.errorCode() : null)
                        .updatedAt(snapshot.updatedAt())
                        .build()))
                .orElseGet(() -> ResponseEntity.notFound().build());
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
        Long userId = (Long) authentication.getPrincipal();
        boolean useAsync = shouldUseAsyncReserve(eventId, userId);
        return useAsync
                ? reserveTicketAsync(eventId, userId)
                : reserveTicketSync(eventId, userId, shouldShadowPublish(eventId, useAsync));
    }

    private ResponseEntity<ResponseReserveTicketDto> reserveTicketSync(Long eventId, Long userId, boolean shadowPublish) {
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
            if (shadowPublish) {
                publishShadowIssueRequest(eventId, userId, eventIdStr, userIdStr, claimResult.remaining());
            }
            return ResponseEntity.ok(response);

        } finally {
            // 성공/실패 무관하게 active ZSet 제거 → 슬롯 반환
            queueStateService.releaseActive(eventIdStr, userIdStr);
        }
    }

    private ResponseEntity<TicketRequestResponseDTO> reserveTicketAsync(Long eventId, Long userId) {
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
        QueueStatusSnapshot snap = ticketStatusService.fetchSnapshot(eventId, userId);
        TicketRequestStatus status = resolveStatus(snap);
        Long aheadCount = resolveAheadCount(snap, status);
        return TicketStatusResponseDTO.builder()
                .status(status)
                .queuePosition(status == TicketRequestStatus.WAITING && snap.queueRank() != null
                        ? snap.queueRank() + 1
                        : null)
                .mySequence(parseLong(snap.seq()))
                .aheadCount(aheadCount)
                .estimatedWaitSeconds(ticketStatusService.getEstimatedWaitSeconds(aheadCount))
                .readyUntil("READY".equals(snap.state()) ? parseLong(snap.readyUntil()) : null)
                .admissionState(resolveAdmissionState(snap.state()))
                .build();
    }

    private TicketRequestResponseDTO buildQueueEnterResponseFromSnapshot(QueueStatusSnapshot snap, TicketRequestStatus status) {
        Long aheadCount = resolveAheadCount(snap, status);
        return TicketRequestResponseDTO.builder()
                .status(status)
                .queuePosition(status == TicketRequestStatus.WAITING && snap.queueRank() != null
                        ? snap.queueRank() + 1
                        : null)
                .mySequence(parseLong(snap.seq()))
                .aheadCount(aheadCount)
                .estimatedWaitSeconds(ticketStatusService.getEstimatedWaitSeconds(aheadCount))
                .readyUntil("READY".equals(snap.state()) ? parseLong(snap.readyUntil()) : null)
                .admissionState(resolveAdmissionState(snap.state()))
                .build();
    }

    private TicketRequestStatus resolveStatus(QueueStatusSnapshot snap) {
        String claimStatus = snap.claimStatus();
        if (claimStatus != null && !claimStatus.isBlank()) {
            try {
                TicketRequestStatus s = TicketRequestStatus.valueOf(claimStatus);
                if (s == TicketRequestStatus.SUCCESS
                        || s == TicketRequestStatus.SOLD_OUT
                        || s == TicketRequestStatus.ALREADY) {
                    return s;
                }
            } catch (IllegalArgumentException ignored) {}
        }
        String state = snap.state();
        if (state == null) return TicketRequestStatus.NONE;
        return switch (state) {
            case "WAITING" -> (snap.stock() == null || snap.stock() <= 0)
                    ? TicketRequestStatus.SOLD_OUT
                    : TicketRequestStatus.WAITING;
            case "READY", "ACTIVE" -> TicketRequestStatus.ADMITTED;
            case "DONE"            -> TicketRequestStatus.SUCCESS;
            default                -> TicketRequestStatus.NONE; // EXPIRED, CANCELLED
        };
    }

    private Long resolveAheadCount(QueueStatusSnapshot snap, TicketRequestStatus status) {
        if (status == TicketRequestStatus.WAITING) {
            return snap.queueRank() != null ? Math.max(snap.queueRank(), 0L) : null;
        }
        if (status == TicketRequestStatus.ADMITTED) {
            return 0L;
        }
        return null;
    }

    private QueueUserState resolveAdmissionState(String state) {
        if ("READY".equals(state))  return QueueUserState.READY;
        if ("ACTIVE".equals(state)) return QueueUserState.ACTIVE;
        return null;
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void markDoneAfterPersistence(String eventId, String userId) {
        try {
            queueStateService.markDone(eventId, userId);
        } catch (Exception e) {
            log.error("markDone 실패, 예매는 이미 DB에 저장됨 eventId={} userId={}", eventId, userId, e);
        }
    }

    private void publishShadowIssueRequest(
            Long eventId,
            Long userId,
            String eventIdStr,
            String userIdStr,
            long remaining
    ) {
        try {
            Long seq = ticketStatusService.getMySequence(eventIdStr, userIdStr);
            ticketIssueEnqueueService.enqueueIssueRequest(
                    eventId,
                    userId,
                    UUID.randomUUID().toString(),
                    remaining,
                    seq,
                    System.currentTimeMillis()
            );
        } catch (Exception e) {
            log.warn(
                    "shadow publish enqueue 실패 eventId={} userId={} remaining={}",
                    eventId,
                    userId,
                    remaining,
                    e
            );
        }
    }

    private boolean shouldUseAsyncReserve(Long eventId, Long userId) {
        if (!asyncReserveEnabled) {
            return false;
        }
        if (!isAllowedEvent(eventId)) {
            return false;
        }
        int rolloutPercent = normalizeRolloutPercent(asyncReserveRolloutPercent);
        if (rolloutPercent <= 0) {
            return false;
        }
        if (rolloutPercent >= 100) {
            return true;
        }
        int bucket = Math.floorMod((eventId + ":" + userId).hashCode(), 100);
        return bucket < rolloutPercent;
    }

    private boolean shouldShadowPublish(Long eventId, boolean asyncSelected) {
        return asyncReserveShadowPublishEnabled && !asyncSelected && isAllowedEvent(eventId);
    }

    private boolean isAllowedEvent(Long eventId) {
        Set<Long> allowedEventIds = parseAllowedEventIds();
        return allowedEventIds.isEmpty() || allowedEventIds.contains(eventId);
    }

    private Set<Long> parseAllowedEventIds() {
        if (asyncReserveAllowedEventIds == null || asyncReserveAllowedEventIds.isBlank()) {
            return Collections.emptySet();
        }
        Set<Long> allowed = new HashSet<>();
        String[] tokens = asyncReserveAllowedEventIds.split(",");
        for (String token : tokens) {
            Long parsedId = safeParseLong(token);
            if (parsedId != null) {
                allowed.add(parsedId);
            }
        }
        return allowed;
    }

    private Long safeParseLong(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String value = rawValue.trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("allowed-event-id 파싱 실패 value={}", value);
            return null;
        }
    }

    private int normalizeRolloutPercent(int rolloutPercent) {
        if (rolloutPercent < 0) {
            return 0;
        }
        return Math.min(rolloutPercent, 100);
    }

    private boolean isTerminal(TicketRequestStatus status) {
        return status == TicketRequestStatus.SUCCESS
                || status == TicketRequestStatus.SOLD_OUT
                || status == TicketRequestStatus.ALREADY
                || status == TicketRequestStatus.PROCESSING;
    }
}
