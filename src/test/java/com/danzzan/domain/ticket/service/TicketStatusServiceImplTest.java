package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.redis.TicketRedisKeys;
import com.danzzan.domain.ticket.redis.TicketRequestStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketStatusServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private RedisScript<List> queueStatusSnapshotScript;

    private TicketStatusServiceImpl ticketStatusService;

    @BeforeEach
    void setUp() {
        ticketStatusService = new TicketStatusServiceImpl(
                redisTemplate,
                queueStatusSnapshotScript
        );
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    @Test
    void getQueueStatusSnapshot_WAITING상태면_순번과_앞대기인원을_계산한다() {
        when(redisTemplate.execute(eq(queueStatusSnapshotScript), anyList()))
                .thenReturn(List.of("", "WAITING", "105", "", "", "100", "500"));

        TicketStatusService.QueueStatusSnapshot snapshot = ticketStatusService.getQueueStatusSnapshot("2", "11");

        assertEquals(TicketRequestStatus.WAITING, snapshot.status());
        assertEquals(105L, snapshot.mySequence());
        assertEquals(5L, snapshot.queuePosition());
        assertEquals(4L, snapshot.aheadCount());
        assertNull(snapshot.readyUntil());
        assertNull(snapshot.admissionState());
    }

    @Test
    void getQueueStatusSnapshot_ACTIVE상태면_activeUntil을_반환한다() {
        when(redisTemplate.execute(eq(queueStatusSnapshotScript), anyList()))
                .thenReturn(List.of("", "ACTIVE", "105", "", "1770000000000", "", ""));

        TicketStatusService.QueueStatusSnapshot snapshot = ticketStatusService.getQueueStatusSnapshot("2", "11");

        assertEquals(TicketRequestStatus.ADMITTED, snapshot.status());
        assertEquals(105L, snapshot.mySequence());
        assertEquals(0L, snapshot.aheadCount());
        assertEquals(1770000000000L, snapshot.readyUntil());
        assertEquals(com.danzzan.domain.ticket.redis.QueueUserState.ACTIVE, snapshot.admissionState());
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
