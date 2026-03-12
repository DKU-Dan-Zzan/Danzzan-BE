package com.danzzan.domain.ticket.controller;

import com.danzzan.domain.ticket.dto.ResponseMyTicketListDto;
import com.danzzan.domain.ticket.dto.ResponseReserveTicketDto;
import com.danzzan.domain.ticket.dto.ResponseTicketEventListDto;
import com.danzzan.domain.ticket.dto.TicketRequestRequestDTO;
import com.danzzan.domain.ticket.dto.TicketRequestResponseDTO;
import com.danzzan.domain.ticket.dto.TicketStatusRequestDTO;
import com.danzzan.domain.ticket.dto.TicketStatusResponseDTO;
import com.danzzan.domain.ticket.exception.AlreadyReservedException;
import com.danzzan.domain.ticket.exception.EventNotOpenException;
import com.danzzan.domain.ticket.exception.EventSoldOutException;
import com.danzzan.domain.ticket.redis.TicketRequestStatus;
import com.danzzan.domain.ticket.service.AdmissionService;
import com.danzzan.domain.ticket.service.ClaimService;
import com.danzzan.domain.ticket.service.QueueService;
import com.danzzan.domain.ticket.service.SlotService;
import com.danzzan.domain.ticket.service.TicketService;
import com.danzzan.domain.ticket.service.TicketStatusService;
import com.danzzan.domain.ticket.service.model.ClaimResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tickets")
@RequiredArgsConstructor
@Tag(name = "사용자 티켓팅", description = "공연 티켓 예매 및 조회 API")
public class TicketController {

    private static final String LEGACY_SUNSET_DATE = "Tue, 30 Jun 2026 23:59:59 GMT";
    private static final String HEADER_DEPRECATION = "Deprecation";
    private static final String HEADER_SUNSET = "Sunset";

    private final TicketService ticketService;
    private final AdmissionService admissionService;
    private final ClaimService claimService;
    private final TicketStatusService ticketStatusService;
    private final QueueService queueService;
    private final SlotService slotService;

    @GetMapping("/events")
    @Operation(summary = "이벤트 목록 조회", description = "티켓팅 가능한 공연 목록을 조회합니다. 로그인 불필요.")
    public ResponseEntity<ResponseTicketEventListDto> getTicketingEvents() {
        return ResponseEntity.ok(ticketService.getTicketingEvents());
    }

    @PostMapping("/{eventId}/queue/enter")
    @Operation(summary = "대기열 진입", description = "대기열에 진입합니다. gate가 있으면 ADMITTED, 없으면 WAITING을 반환합니다.")
    public ResponseEntity<TicketRequestResponseDTO> enterQueue(
            @PathVariable Long eventId,
            Authentication authentication
    ) {
        Long userId = (Long) authentication.getPrincipal();
        String eventIdStr = String.valueOf(eventId);
        String userIdStr = String.valueOf(userId);

        // 이미 터미널 상태(SUCCESS/SOLD_OUT/ALREADY)면 큐 진입 없이 바로 반환
        TicketRequestStatus currentStatus = ticketStatusService.getStatus(eventIdStr, userIdStr);
        if (isTerminal(currentStatus)) {
            return ResponseEntity.ok(TicketRequestResponseDTO.builder()
                    .status(currentStatus)
                    .build());
        }

        // 대기열 진입 (이미 있으면 addIfAbsent로 무시)
        queueService.enterQueue(eventIdStr, userIdStr);

        // gate 여부로 ADMITTED / WAITING 판단
        TicketRequestStatus status = ticketStatusService.getStatus(eventIdStr, userIdStr);

        if (status == TicketRequestStatus.ADMITTED) {
            return ResponseEntity.ok(TicketRequestResponseDTO.builder()
                    .status(TicketRequestStatus.ADMITTED)
                    .build());
        }

        Long queuePosition = ticketStatusService.getQueuePosition(eventIdStr, userIdStr);
        return ResponseEntity.ok(TicketRequestResponseDTO.builder()
                .status(TicketRequestStatus.WAITING)
                .queuePosition(queuePosition)
                .build());
    }

    @GetMapping("/{eventId}/queue/status")
    @Operation(summary = "대기열 상태 조회", description = "현재 대기열 상태를 조회합니다.")
    public ResponseEntity<TicketStatusResponseDTO> getQueueStatus(
            @PathVariable Long eventId,
            Authentication authentication
    ) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(buildStatusResponse(String.valueOf(eventId), String.valueOf(userId)));
    }

    @PostMapping("/{eventId}/reserve")
    @Operation(summary = "티켓 예매", description = "gate 확인 후 Lua로 재고 차감, DB 저장. 완료 후 슬롯 즉시 반환.")
    public ResponseEntity<ResponseReserveTicketDto> reserveTicket(
            @PathVariable Long eventId,
            Authentication authentication
    ) {
        Long userId = (Long) authentication.getPrincipal();
        String eventIdStr = String.valueOf(eventId);
        String userIdStr = String.valueOf(userId);

        // gate 확인 — 스케줄러가 허가한 유저만 통과
        if (admissionService.admit(eventIdStr, userIdStr) != TicketRequestStatus.ADMITTED) {
            throw new EventNotOpenException("입장 허가 대기 중입니다. 잠시 후 다시 시도해주세요.");
        }

        try {
            // Lua Script 원자적 재고 차감
            ClaimResult claimResult = claimService.claim(eventIdStr, userIdStr);

            if (claimResult.status() == TicketRequestStatus.SOLD_OUT) {
                throw new EventSoldOutException();
            }
            if (claimResult.status() == TicketRequestStatus.ALREADY) {
                throw new AlreadyReservedException();
            }

            // DB 저장 (실패 시 Redis 롤백)
            try {
                return ResponseEntity.ok(
                        ticketService.persistAndBuildResponse(userId, eventId, claimResult.remaining()));
            } catch (AlreadyReservedException e) {
                throw e;
            } catch (Exception e) {
                claimService.rollback(eventIdStr, userIdStr);
                throw e;
            }

        } finally {
            // 성공/실패 무관하게 슬롯 즉시 반환 → 다음 대기자 빠른 입장
            slotService.releaseSlot(eventIdStr, userIdStr);
        }
    }

    @GetMapping("/me")
    @Operation(summary = "내 티켓 조회", description = "내가 예매한 티켓 목록을 조회합니다. 로그인 필요.")
    public ResponseEntity<ResponseMyTicketListDto> getMyTickets(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ticketService.getMyTickets(userId));
    }

    // ── v1 레거시 엔드포인트 (deprecated) ────────────────────────────────────

    @PostMapping("/request")
    @Operation(
            summary = "티켓 요청(v1, deprecated)",
            description = "레거시 호환용 엔드포인트입니다. 신규 클라이언트는 /tickets/{eventId}/queue/enter 사용을 권장합니다.",
            deprecated = true
    )
    public ResponseEntity<TicketRequestResponseDTO> requestTicket(
            @Valid @RequestBody TicketRequestRequestDTO request
    ) {
        String eventIdStr = request.getEventId();
        String userIdStr = request.getUserId();

        queueService.enterQueue(eventIdStr, userIdStr);

        TicketRequestStatus status = ticketStatusService.getStatus(eventIdStr, userIdStr);
        Long queuePosition = status == TicketRequestStatus.WAITING
                ? ticketStatusService.getQueuePosition(eventIdStr, userIdStr)
                : null;

        return legacyResponse(TicketRequestResponseDTO.builder()
                .status(status)
                .queuePosition(queuePosition)
                .build());
    }

    @GetMapping("/status")
    @Operation(
            summary = "티켓 상태 조회(v1, deprecated)",
            description = "레거시 호환용 엔드포인트입니다. 신규 클라이언트는 /tickets/{eventId}/queue/status 사용을 권장합니다.",
            deprecated = true
    )
    public ResponseEntity<TicketStatusResponseDTO> getTicketStatus(
            @Valid @ModelAttribute TicketStatusRequestDTO request
    ) {
        return legacyResponse(buildStatusResponse(request.getEventId(), request.getUserId()));
    }

    // ── 내부 헬퍼 ────────────────────────────────────────────────────────────

    private TicketStatusResponseDTO buildStatusResponse(String eventId, String userId) {
        TicketRequestStatus status = ticketStatusService.getStatus(eventId, userId);
        Long queuePosition = status == TicketRequestStatus.WAITING
                ? ticketStatusService.getQueuePosition(eventId, userId)
                : null;
        return TicketStatusResponseDTO.builder()
                .status(status)
                .queuePosition(queuePosition)
                .build();
    }

    private boolean isTerminal(TicketRequestStatus status) {
        return status == TicketRequestStatus.SUCCESS
                || status == TicketRequestStatus.SOLD_OUT
                || status == TicketRequestStatus.ALREADY;
    }

    private <T> ResponseEntity<T> legacyResponse(T body) {
        return ResponseEntity.ok()
                .header(HEADER_DEPRECATION, "true")
                .header(HEADER_SUNSET, LEGACY_SUNSET_DATE)
                .body(body);
    }
}
