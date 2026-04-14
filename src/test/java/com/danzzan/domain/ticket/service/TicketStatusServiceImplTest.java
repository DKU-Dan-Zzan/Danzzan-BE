package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.redis.TicketRedisKeys;
import com.danzzan.domain.ticket.redis.TicketRequestStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketStatusServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private QueueService queueService;

    @Mock
    private QueueStateService queueStateService;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private TicketStatusServiceImpl ticketStatusService;

    @BeforeEach
    void setUp() {
        ticketStatusService = new TicketStatusServiceImpl(redisTemplate, queueService, queueStateService);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void setFailed_FAILED상태를_5분TTL로_저장하고_processing메타를_삭제한다() {
        ticketStatusService.setFailed("10", "1", 300L);

        verify(valueOperations).set(
                eq(TicketRedisKeys.statusKey("10", "1")),
                eq(TicketRequestStatus.FAILED.name()),
                eq(300L),
                eq(TimeUnit.SECONDS)
        );
        verify(redisTemplate).delete(eq(TicketRedisKeys.processingMetaKey("10", "1")));
    }
}
