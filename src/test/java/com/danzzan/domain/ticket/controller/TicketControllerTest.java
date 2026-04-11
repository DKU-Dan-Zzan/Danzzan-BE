package com.danzzan.domain.ticket.controller;

import com.danzzan.domain.ticket.dto.ResponseMyTicketDto;
import com.danzzan.domain.ticket.dto.ResponseReserveTicketDto;
import com.danzzan.domain.ticket.redis.QueueUserState;
import com.danzzan.domain.ticket.exception.EventNotOpenException;
import com.danzzan.domain.ticket.exception.EventSoldOutException;
import com.danzzan.domain.event.model.entity.TicketingStatus;
import com.danzzan.domain.ticket.redis.TicketRequestStatus;
import com.danzzan.domain.ticket.metrics.TicketingMetrics;
import com.danzzan.domain.ticket.service.ClaimService;
import com.danzzan.domain.ticket.service.QueueService;
import com.danzzan.domain.ticket.service.QueueStateService;
import com.danzzan.domain.ticket.service.TicketIssueEnqueueService;
import com.danzzan.domain.ticket.service.TicketService;
import com.danzzan.domain.ticket.service.TicketStatusService;
import com.danzzan.domain.ticket.service.model.ClaimResult;
import com.danzzan.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TicketControllerTest {

    private MockMvc mockMvc;

    @Mock private TicketService ticketService;
    @Mock private ClaimService claimService;
    @Mock private TicketStatusService ticketStatusService;
    @Mock private QueueService queueService;
    @Mock private QueueStateService queueStateService;
    @Mock private TicketingMetrics ticketingMetrics;
    @Mock private TicketIssueEnqueueService ticketIssueEnqueueService;
    private static final Principal USER_AUTH = new TestingAuthenticationToken(1L, null);

    @BeforeEach
    void setUp() {
        TicketController controller = new TicketController(
                ticketService, claimService, ticketStatusService, queueService, queueStateService, ticketingMetrics,
                ticketIssueEnqueueService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── POST /queue/enter ─────────────────────────────────────────────────────

    @Test
    void enterQueue_대기중이면_queuePosition을_반환한다() throws Exception {
        when(ticketStatusService.getStatus(eq("10"), eq("1")))
                .thenReturn(TicketRequestStatus.NONE, TicketRequestStatus.WAITING);
        when(ticketService.hasTicket(eq(1L), eq(10L))).thenReturn(false);
        when(ticketService.getTicketingStatus(eq(10L))).thenReturn(TicketingStatus.OPEN);
        when(queueService.enterQueue(eq("10"), eq("1"))).thenReturn(1L);
        when(ticketStatusService.getQueuePosition(eq("10"), eq("1"))).thenReturn(5L);

        mockMvc.perform(post("/tickets/10/queue/enter").principal(USER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.queuePosition").value(5))
                .andExpect(jsonPath("$.remaining").doesNotExist());
    }

    @Test
    void enterQueue_WAITING이고_queuePosition_없으면_필드를_생략한다() throws Exception {
        when(ticketStatusService.getStatus(eq("10"), eq("1")))
                .thenReturn(TicketRequestStatus.NONE, TicketRequestStatus.WAITING);
        when(ticketService.hasTicket(eq(1L), eq(10L))).thenReturn(false);
        when(ticketService.getTicketingStatus(eq(10L))).thenReturn(TicketingStatus.OPEN);
        when(queueService.enterQueue(eq("10"), eq("1"))).thenReturn(1L);
        when(ticketStatusService.getQueuePosition(eq("10"), eq("1"))).thenReturn(null);

        mockMvc.perform(post("/tickets/10/queue/enter").principal(USER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.queuePosition").doesNotExist());
    }

    @Test
    void enterQueue_READY이면_ADMITTED를_반환한다() throws Exception {
        when(ticketStatusService.getStatus(eq("10"), eq("1")))
                .thenReturn(TicketRequestStatus.NONE, TicketRequestStatus.ADMITTED);
        when(ticketService.hasTicket(eq(1L), eq(10L))).thenReturn(false);
        when(ticketService.getTicketingStatus(eq(10L))).thenReturn(TicketingStatus.OPEN);
        when(queueService.enterQueue(eq("10"), eq("1"))).thenReturn(1L);

        mockMvc.perform(post("/tickets/10/queue/enter").principal(USER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ADMITTED"))
                .andExpect(jsonPath("$.queuePosition").doesNotExist());

        verify(ticketStatusService, never()).getQueuePosition(any(), any());
    }

    @Test
    void enterQueue_이미_SUCCESS이면_큐_진입_없이_즉시_반환한다() throws Exception {
        when(ticketStatusService.getStatus(eq("10"), eq("1")))
                .thenReturn(TicketRequestStatus.SUCCESS);

        mockMvc.perform(post("/tickets/10/queue/enter").principal(USER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        verify(queueService, never()).enterQueue(any(), any());
    }

    @Test
    void enterQueue_이미_ALREADY이면_큐_진입_없이_즉시_반환한다() throws Exception {
        when(ticketStatusService.getStatus(eq("10"), eq("1")))
                .thenReturn(TicketRequestStatus.ALREADY);

        mockMvc.perform(post("/tickets/10/queue/enter").principal(USER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ALREADY"));

        verify(queueService, never()).enterQueue(any(), any());
    }

    // ── POST /activate ────────────────────────────────────────────────────────

    @Test
    void activate_READY이면_ADMITTED를_반환한다() throws Exception {
        when(queueStateService.activateIfReady(eq("10"), eq("1"))).thenReturn(1L);

        mockMvc.perform(post("/tickets/10/activate").principal(USER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ADMITTED"));
    }

    @Test
    void activate_READY_아니면_400을_반환한다() throws Exception {
        when(queueStateService.activateIfReady(eq("10"), eq("1"))).thenReturn(0L);

        mockMvc.perform(post("/tickets/10/activate").principal(USER_AUTH))
                .andExpect(status().isBadRequest());
    }

    @Test
    void activate_READY_만료이면_400을_반환한다() throws Exception {
        when(queueStateService.activateIfReady(eq("10"), eq("1"))).thenReturn(-1L);

        mockMvc.perform(post("/tickets/10/activate").principal(USER_AUTH))
                .andExpect(status().isBadRequest());
    }

    // ── POST /reserve ─────────────────────────────────────────────────────────

    @Test
    void reserve_ACTIVE이면_SUCCESS를_반환한다() throws Exception {
        when(claimService.claim(eq("10"), eq("1"))).thenReturn(ClaimResult.success(42L));

        ResponseMyTicketDto mockTicket = ResponseMyTicketDto.builder()
                .id("999").status("issued").eventName("테스트공연")
                .eventDate("05월 13일 (화) 19:00").issuedAt("2026-05-11 13:00")
                .seat("단국존 순번 #42").queueNumber(42).wristbandIssued(false)
                .venue("단국존").contact("운영본부").eventDescription("테스트")
                .build();
        when(ticketService.persistAndBuildResponse(eq(1L), eq(10L), eq(42L)))
                .thenReturn(new ResponseReserveTicketDto(42, mockTicket));

        mockMvc.perform(post("/tickets/10/reserve").principal(USER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueNumber").value(42));

        verify(queueStateService).markDone(eq("10"), eq("1"));
        verify(queueStateService).releaseActive(eq("10"), eq("1"));
    }

    @Test
    void reserve_markDone가_실패해도_rollback하지_않고_SUCCESS를_반환한다() throws Exception {
        when(claimService.claim(eq("10"), eq("1"))).thenReturn(ClaimResult.success(42L));

        ResponseMyTicketDto mockTicket = ResponseMyTicketDto.builder()
                .id("999").status("issued").eventName("테스트공연")
                .eventDate("05월 13일 (화) 19:00").issuedAt("2026-05-11 13:00")
                .seat("단국존 순번 #42").queueNumber(42).wristbandIssued(false)
                .venue("단국존").contact("운영본부").eventDescription("테스트")
                .build();
        when(ticketService.persistAndBuildResponse(eq(1L), eq(10L), eq(42L)))
                .thenReturn(new ResponseReserveTicketDto(42, mockTicket));
        doThrow(new RuntimeException("sync failed"))
                .when(queueStateService).markDone(eq("10"), eq("1"));

        mockMvc.perform(post("/tickets/10/reserve").principal(USER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueNumber").value(42));

        verify(claimService, never()).rollback(eq("10"), eq("1"));
        verify(queueStateService).releaseActive(eq("10"), eq("1"));
    }

    @Test
    void reserve_ACTIVE_아니면_400을_반환하고_claim을_호출하지_않는다() throws Exception {
        when(claimService.claim(eq("10"), eq("1")))
                .thenThrow(new EventNotOpenException("유의사항 화면 진입 후 예매 가능합니다."));

        mockMvc.perform(post("/tickets/10/reserve").principal(USER_AUTH))
                .andExpect(status().isBadRequest());

        verify(claimService).claim(eq("10"), eq("1"));
        verify(queueStateService).releaseActive(eq("10"), eq("1"));
    }

    @Test
    void reserve_SOLD_OUT이면_409_반환하고_releaseActive를_호출한다() throws Exception {
        when(claimService.claim(eq("10"), eq("1"))).thenReturn(ClaimResult.soldOut());

        mockMvc.perform(post("/tickets/10/reserve").principal(USER_AUTH))
                .andExpect(status().isConflict());

        verify(queueStateService).releaseActive(eq("10"), eq("1"));
    }

    // ── GET /queue/status ─────────────────────────────────────────────────────

    @Test
    void getQueueStatus_WAITING이면_queuePosition을_포함한다() throws Exception {
        when(ticketStatusService.getStatus(eq("10"), eq("1"))).thenReturn(TicketRequestStatus.WAITING);
        when(ticketStatusService.getQueuePosition(eq("10"), eq("1"))).thenReturn(3L);
        when(ticketStatusService.getMySequence(eq("10"), eq("1"))).thenReturn(12L);
        when(ticketStatusService.getAheadCount(eq("10"), eq("1"))).thenReturn(2L);
        when(ticketStatusService.getEstimatedWaitSeconds(eq(2L))).thenReturn(180L);

        mockMvc.perform(get("/tickets/10/queue/status").principal(USER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.queuePosition").value(3))
                .andExpect(jsonPath("$.mySequence").value(12))
                .andExpect(jsonPath("$.aheadCount").value(2))
                .andExpect(jsonPath("$.estimatedWaitSeconds").value(180));
    }

    @Test
    void getQueueStatus_ALREADY이면_queuePosition을_생략하고_getQueuePosition을_호출하지_않는다() throws Exception {
        when(ticketStatusService.getStatus(eq("10"), eq("1"))).thenReturn(TicketRequestStatus.ALREADY);

        mockMvc.perform(get("/tickets/10/queue/status").principal(USER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ALREADY"))
                .andExpect(jsonPath("$.queuePosition").doesNotExist());

        verify(ticketStatusService, never()).getQueuePosition(any(), any());
    }

    @Test
    void getQueueStatus_READY이면_readyUntil과_admissionState를_포함한다() throws Exception {
        when(ticketStatusService.getStatus(eq("10"), eq("1"))).thenReturn(TicketRequestStatus.ADMITTED);
        when(ticketStatusService.getReadyUntil(eq("10"), eq("1"))).thenReturn(1773486180000L);
        when(ticketStatusService.getAdmissionState(eq("10"), eq("1"))).thenReturn(QueueUserState.READY);

        mockMvc.perform(get("/tickets/10/queue/status").principal(USER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ADMITTED"))
                .andExpect(jsonPath("$.readyUntil").value(1773486180000L))
                .andExpect(jsonPath("$.admissionState").value("READY"));
    }
}
