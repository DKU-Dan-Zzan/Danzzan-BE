package com.danzzan.domain.ticket.controller;

import com.danzzan.domain.ticket.dto.ResponseTicketEventListDto;
import com.danzzan.domain.ticket.service.ClaimService;
import com.danzzan.domain.ticket.service.QueueService;
import com.danzzan.domain.ticket.service.QueueStateService;
import com.danzzan.domain.ticket.service.TicketService;
import com.danzzan.domain.ticket.service.TicketStatusService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TicketSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketService ticketService;

    @MockitoBean
    private QueueStateService queueStateService;

    @MockitoBean
    private ClaimService claimService;

    @MockitoBean
    private TicketStatusService ticketStatusService;

    @MockitoBean
    private QueueService queueService;

    @Test
    void getMyTickets_비인증_요청은_403과_빈본문을_반환한다() throws Exception {
        MvcResult result = mockMvc.perform(get("/tickets/me"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("protectedTicketEndpoints")
    void ticket_인증필요_엔드포인트는_비인증_요청을_차단한다(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized());
    }

    static Stream<MockHttpServletRequestBuilder> protectedTicketEndpoints() {
        return Stream.of(
                post("/tickets/10/queue/enter"),
                get("/tickets/10/queue/status"),
                post("/tickets/10/reserve")
        );
    }

    @Test
    void getTicketEvents_비인증_요청은_허용된다() throws Exception {
        when(ticketService.getTicketingEvents()).thenReturn(new ResponseTicketEventListDto(List.of()));

        mockMvc.perform(get("/tickets/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }
}
