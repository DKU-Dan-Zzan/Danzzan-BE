package com.danzzan.domain.ticket.scheduler;

import com.danzzan.domain.ticket.service.OutboxPublisherService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherSchedulerTest {

    @Mock
    private OutboxPublisherService outboxPublisherService;

    @Test
    void run_퍼블리셔예외가_발생해도_스케줄러는_중단하지_않는다() {
        OutboxPublisherScheduler scheduler = new OutboxPublisherScheduler(outboxPublisherService);
        doThrow(new RuntimeException("boom")).when(outboxPublisherService).publishPendingBatch();

        assertDoesNotThrow(scheduler::run);
        verify(outboxPublisherService).publishPendingBatch();
    }
}
