package com.danzzan.domain.ticket.controller;

import com.danzzan.domain.ticket.dto.ResponseMyTicketDto;
import com.danzzan.domain.ticket.dto.ResponseReserveTicketDto;
import com.danzzan.domain.ticket.exception.AlreadyReservedException;
import com.danzzan.domain.ticket.exception.EventNotOpenException;
import com.danzzan.domain.ticket.exception.EventSoldOutException;
import com.danzzan.domain.ticket.redis.TicketRequestStatus;
import com.danzzan.global.exception.GlobalExceptionHandler;
import com.danzzan.domain.ticket.service.AdmissionService;
import com.danzzan.domain.ticket.service.ClaimService;
import com.danzzan.domain.ticket.service.QueueService;
import com.danzzan.domain.ticket.service.SlotService;
import com.danzzan.domain.ticket.service.TicketService;
import com.danzzan.domain.ticket.service.TicketStatusService;
import com.danzzan.domain.ticket.service.model.ClaimResult;
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
    @Mock private AdmissionService admissionService;
    @Mock private ClaimService claimService;
    @Mock private TicketStatusService ticketStatusService;
    @Mock private QueueService queueService;
    @Mock private SlotService slotService;

    private static final Principal USER_AUTH = new TestingAuthenticationToken(1L, null);

    @BeforeEach
    void setUp() {
        TicketController controller = new TicketController(
                ticketService, admissionService, claimService,
                ticketStatusService, queueService, slotService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── POST /queue/enter 테스트 ──────────────────────────────────────────────

    @Test
    void enterQueue_대기중이면_queuePosition을_반환한다() throws Exception {
        // 1차 getStatus(터미널 체크): NONE → 터미널 아님, 큐 진입 진행
        // 2차 getStatus(gate 체크): WAITING
        when(ticketStatusService.getStatus(eq("10"), eq("1")))
                .thenReturn(TicketRequestStatus.NONE, TicketRequestStatus.WAITING);
        when(ticketStatusService.getQueuePosition(eq("10"), eq("1"))).thenReturn(5L);

        mockMvc.perform(post("/tickets/10/queue/enter").principal(USER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.queuePosition").value(5))
                .andExpect(jsonPath("$.remaining").doesNotExist());
    }

    @Test
    void enterQueue_대기열에_없으면_queuePosition을_생략한다() throws Exception {
        when(ticketStatusService.getStatus(eq("10"), eq("1")))
                .thenReturn(TicketRequestStatus.NONE, TicketRequestStatus.WAITING);
        when(ticketStatusService.getQueuePosition(eq("10"), eq("1"))).thenReturn(null);

        mockMvc.perform(post("/tickets/10/queue/enter").principal(USER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.queuePosition").doesNotExist());
    }

    @Test
    void enterQueue_이미_ADMITTED이면_ADMITTED를_반환하고_큐에_넣는다() throws Exception {
        when(ticketStatusService.getStatus(eq("10"), eq("1")))
                .thenReturn(TicketRequestStatus.NONE, TicketRequestStatus.ADMITTED);

        mockMvc.perform(post("/tickets/10/queue/enter").principal(USER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ADMITTED"))
                .andExpect(jsonPath("$.queuePosition").doesNotExist());

        verify(queueService).enterQueue(eq("10"), eq("1"));
        verify(ticketStatusService, never()).getQueuePosition(any(), any());
    }

    @Test
    void enterQueue_이미_ALREADY이면_큐_진입_없이_즉시_반환한다() throws Exception {
        // 터미널 상태 → 큐 진입 없이 바로 반환
        when(ticketStatusService.getStatus(eq("10"), eq("1")))
                .thenReturn(TicketRequestStatus.ALREADY);

        mockMvc.perform(post("/tickets/10/queue/enter").principal(USER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ALREADY"))
                .andExpect(jsonPath("$.queuePosition").doesNotExist());

        verify(queueService, never()).enterQueue(any(), any());
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

    // ── POST /reserve 테스트 ──────────────────────────────────────────────────

    @Test
    void reserve_gate_있으면_SUCCESS를_반환한다() throws Exception {
        when(admissionService.admit(eq("10"), eq("1"))).thenReturn(TicketRequestStatus.ADMITTED);
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

        // 성공 후 슬롯 반환 검증
        verify(slotService).releaseSlot(eq("10"), eq("1"));
    }

    @Test
    void reserve_gate_없으면_400을_반환한다() throws Exception {
        when(admissionService.admit(eq("10"), eq("1"))).thenReturn(TicketRequestStatus.WAITING);

        mockMvc.perform(post("/tickets/10/reserve").principal(USER_AUTH))
                .andExpect(status().isBadRequest());

        // gate 없으면 claim도 호출하지 않음
        verify(claimService, never()).claim(any(), any());
        // gate를 획득하지 못한 요청은 reserve 본 처리에 들어가지 않으므로 슬롯 반환도 호출하지 않는다.
        verify(slotService, never()).releaseSlot(any(), any());
    }

    @Test
    void reserve_SOLD_OUT이면_슬롯을_반환한다() throws Exception {
        when(admissionService.admit(eq("10"), eq("1"))).thenReturn(TicketRequestStatus.ADMITTED);
        when(claimService.claim(eq("10"), eq("1"))).thenReturn(ClaimResult.soldOut());

        mockMvc.perform(post("/tickets/10/reserve").principal(USER_AUTH))
                .andExpect(status().isConflict());

        verify(slotService).releaseSlot(eq("10"), eq("1"));
    }

    // ── GET /queue/status 테스트 ──────────────────────────────────────────────

    @Test
    void getQueueStatus_WAITING이면_queuePosition을_포함한다() throws Exception {
        when(ticketStatusService.getStatus(eq("10"), eq("1"))).thenReturn(TicketRequestStatus.WAITING);
        when(ticketStatusService.getQueuePosition(eq("10"), eq("1"))).thenReturn(3L);

        mockMvc.perform(get("/tickets/10/queue/status").principal(USER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.queuePosition").value(3));
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
}
