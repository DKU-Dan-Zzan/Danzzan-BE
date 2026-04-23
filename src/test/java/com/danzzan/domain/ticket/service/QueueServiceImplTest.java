package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.redis.TicketRedisKeys;
import com.danzzan.domain.ticket.redis.TicketRequestStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class QueueServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private RedisScript<List> enterQueueScript;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private QueueServiceImpl queueService;

    @BeforeEach
    void setUp() {
        queueService = new QueueServiceImpl(redisTemplate, enterQueueScript);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    @Test
    void enterQueue_processing이면_선조회로_즉시반환하고_Lua를_호출하지_않는다() {
        when(redisTemplate.executePipelined(org.mockito.ArgumentMatchers.<RedisCallback<Object>>any()))
                .thenReturn(Arrays.asList("PROCESSING", null, "OPEN"));
        when(hashOperations.multiGet(
                eq(TicketRedisKeys.processingMetaKey("10", "1")),
                eq(List.of("requestId", "acceptedAt"))
        )).thenReturn(List.of("req-1", "1775917200000"));

        QueueService.QueueEnterSnapshot snapshot = queueService.enterQueue("10", "1");

        assertEquals(TicketRequestStatus.PROCESSING, snapshot.status());
        assertEquals("req-1", snapshot.requestId());
        assertEquals(1775917200000L, snapshot.acceptedAt());
        verify(redisTemplate, never()).execute(eq(enterQueueScript), anyList(), anyString(), anyString());
    }

    @Test
    void enterQueue_waiting상태면_선조회로_즉시반환하고_Lua를_호출하지_않는다() {
        when(redisTemplate.executePipelined(org.mockito.ArgumentMatchers.<RedisCallback<Object>>any()))
                .thenReturn(Arrays.asList(null, "WAITING", "OPEN"));

        QueueService.QueueEnterSnapshot snapshot = queueService.enterQueue("10", "1");

        assertEquals(TicketRequestStatus.WAITING, snapshot.status());
        verify(redisTemplate, never()).execute(eq(enterQueueScript), anyList(), anyString(), anyString());
    }

    @Test
    void enterQueue_신규유저면_Lua를_호출한다() {
        when(redisTemplate.executePipelined(org.mockito.ArgumentMatchers.<RedisCallback<Object>>any()))
                .thenReturn(Arrays.asList(null, null, "OPEN"));
        when(redisTemplate.execute(eq(enterQueueScript), anyList(), eq("1"), anyString()))
                .thenReturn(List.of("WAITING", "0", "", "0"));

        QueueService.QueueEnterSnapshot snapshot = queueService.enterQueue("10", "1");

        assertEquals(TicketRequestStatus.WAITING, snapshot.status());
        verify(redisTemplate).execute(eq(enterQueueScript), anyList(), eq("1"), anyString());
    }
}
