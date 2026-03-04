package com.danzzan.ticketing.domain.ticket.controller;

import com.danzzan.ticketing.domain.ticket.redis.TicketRequestStatus;
import com.danzzan.ticketing.domain.ticket.service.AdmissionService;
import com.danzzan.ticketing.domain.ticket.service.ClaimService;
import com.danzzan.ticketing.domain.ticket.service.TicketService;
import com.danzzan.ticketing.domain.ticket.service.TicketStatusService;
import com.danzzan.ticketing.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TicketControllerStatusBindingTest {

    @Mock
    private TicketService ticketService;

    @Mock
    private AdmissionService admissionService;

    @Mock
    private ClaimService claimService;

    @Mock
    private TicketStatusService ticketStatusService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TicketController controller = new TicketController(
                ticketService,
                admissionService,
                claimService,
                ticketStatusService
        );

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void bindsQueryParametersAndReturnsStatus() throws Exception {
        when(ticketStatusService.getStatus("festival-day1", "32221902"))
                .thenReturn(TicketRequestStatus.NONE);

        mockMvc.perform(get("/tickets/status")
                        .param("eventId", "festival-day1")
                        .param("userId", "32221902"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NONE"));

        verify(ticketStatusService).getStatus("festival-day1", "32221902");
    }
}
