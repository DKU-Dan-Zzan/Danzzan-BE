package com.danzzan.domain.ticket.controller;

import com.danzzan.domain.ticket.redis.TicketRequestStatus;
import com.danzzan.domain.ticket.service.AdmissionService;
import com.danzzan.domain.ticket.service.ClaimService;
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

    @Mock
    private TicketService ticketService;
    @Mock
    private AdmissionService admissionService;
    @Mock
    private ClaimService claimService;
    @Mock
    private TicketStatusService ticketStatusService;

    // Authentication은 Principal을 구현하므로 MockMvc의 .principal()로 주입 가능
    private static final Principal USER_AUTH = new TestingAuthenticationToken(1L, null);

    @BeforeEach
    void setUp() {
        TicketController controller = new TicketController(ticketService, admissionService, claimService, ticketStatusService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void enterQueue_WAITING_returns_queuePosition() throws Exception {
        when(admissionService.admit(eq("10"), eq("1"))).thenReturn(TicketRequestStatus.WAITING);
        when(ticketStatusService.getQueuePosition(eq("10"), eq("1"))).thenReturn(5L);

        mockMvc.perform(post("/tickets/10/queue/enter").principal(USER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.queuePosition").value(5))
                .andExpect(jsonPath("$.remaining").doesNotExist());
    }

    @Test
    void enterQueue_WAITING_queuePosition_null_when_not_in_queue() throws Exception {
        // 대기열 Sorted Set에 유저가 없는 경우 (ZRANK가 null 반환)
        when(admissionService.admit(eq("10"), eq("1"))).thenReturn(TicketRequestStatus.WAITING);
        when(ticketStatusService.getQueuePosition(eq("10"), eq("1"))).thenReturn(null);

        mockMvc.perform(post("/tickets/10/queue/enter").principal(USER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.queuePosition").doesNotExist());
    }

    @Test
    void enterQueue_ALREADY_does_not_call_getQueuePosition() throws Exception {
        when(admissionService.admit(eq("10"), eq("1"))).thenReturn(TicketRequestStatus.ALREADY);

        mockMvc.perform(post("/tickets/10/queue/enter").principal(USER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ALREADY"))
                .andExpect(jsonPath("$.queuePosition").doesNotExist());

        verify(ticketStatusService, never()).getQueuePosition(any(), any());
    }

    @Test
    void enterQueue_SUCCESS_returns_remaining() throws Exception {
        when(admissionService.admit(eq("10"), eq("1"))).thenReturn(TicketRequestStatus.ADMITTED);
        when(claimService.claim(eq("10"), eq("1"))).thenReturn(ClaimResult.success(42L));

        mockMvc.perform(post("/tickets/10/queue/enter").principal(USER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.remaining").value(42))
                .andExpect(jsonPath("$.queuePosition").doesNotExist());
    }

    @Test
    void enterQueue_SOLD_OUT_returns_null_remaining() throws Exception {
        when(admissionService.admit(eq("10"), eq("1"))).thenReturn(TicketRequestStatus.ADMITTED);
        when(claimService.claim(eq("10"), eq("1"))).thenReturn(ClaimResult.soldOut());

        mockMvc.perform(post("/tickets/10/queue/enter").principal(USER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SOLD_OUT"))
                .andExpect(jsonPath("$.remaining").doesNotExist())
                .andExpect(jsonPath("$.queuePosition").doesNotExist());
    }

    // ── /queue/status 엔드포인트 테스트 ──────────────────────────────────────

    @Test
    void getQueueStatus_WAITING_returns_queuePosition() throws Exception {
        when(ticketStatusService.getStatus(eq("10"), eq("1"))).thenReturn(TicketRequestStatus.WAITING);
        when(ticketStatusService.getQueuePosition(eq("10"), eq("1"))).thenReturn(3L);

        mockMvc.perform(get("/tickets/10/queue/status").principal(USER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.queuePosition").value(3));
    }

    @Test
    void getQueueStatus_ALREADY_does_not_include_queuePosition() throws Exception {
        when(ticketStatusService.getStatus(eq("10"), eq("1"))).thenReturn(TicketRequestStatus.ALREADY);

        mockMvc.perform(get("/tickets/10/queue/status").principal(USER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ALREADY"))
                .andExpect(jsonPath("$.queuePosition").doesNotExist());

        verify(ticketStatusService, never()).getQueuePosition(any(), any());
    }
}
