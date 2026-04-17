package com.danzzan.domain.ticket.scheduler;

import com.danzzan.domain.ticket.service.TicketIssueCompensationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TicketIssueCompensationSchedulerTest {

    @Mock
    private TicketIssueCompensationService ticketIssueCompensationService;

    @Test
    void run_보상워커예외가_발생해도_스케줄러는_중단하지_않는다() {
        TicketIssueCompensationScheduler scheduler = new TicketIssueCompensationScheduler(ticketIssueCompensationService);
        doThrow(new RuntimeException("boom")).when(ticketIssueCompensationService).retryPendingCompensations();

        assertDoesNotThrow(scheduler::run);
        verify(ticketIssueCompensationService).retryPendingCompensations();
    }
}
