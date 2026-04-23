package com.danzzan.domain.ticket.service;

import com.danzzan.domain.event.repository.FestivalEventRepository;
import com.danzzan.domain.ticket.redis.TicketRedisKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class QueueStateServiceImplTest {

    @Mock
    StringRedisTemplate redisTemplate;
    @Mock
    FestivalEventRepository eventRepository;
    @Mock
    RedisScript<Long> readyToActiveScript;
    @Mock
    RedisScript<Long> admitWaitingUsersBatchScript;
    @Mock
    RedisScript<List> expireReadyUsersScript;
    @Mock
    RedisScript<List> expireActiveUsersScript;

    private QueueStateServiceImpl sut;

    @BeforeEach
    void setUp() {
        sut = new QueueStateServiceImpl(
                redisTemplate,
                eventRepository,
                readyToActiveScript,
                admitWaitingUsersBatchScript,
                expireReadyUsersScript,
                expireActiveUsersScript
        );
    }

    @Test
    void admitWaitingUsers_배치상한을_Lua_인자로_전달한다() {
        sut.admitWaitingUsers("1", 1000L, 300, 77);

        verify(redisTemplate).execute(
                eq(admitWaitingUsersBatchScript),
                eq(List.of(
                        TicketRedisKeys.queueKey("1"),
                        TicketRedisKeys.readyKey("1"),
                        TicketRedisKeys.activeKey("1"),
                        TicketRedisKeys.stockKey("1"),
                        TicketRedisKeys.admittedSeqKey("1")
                )),
                eq(TicketRedisKeys.queueUserPrefix("1")),
                anyString(),
                eq("1000"),
                eq("300"),
                eq("77")
        );
    }

    @Test
    void expireReadyUsers_설정된_배치크기를_Lua_인자로_전달한다() {
        ReflectionTestUtils.setField(sut, "expireBatchSize", 77);

        sut.expireReadyUsers("1");

        verify(redisTemplate).execute(
                eq(expireReadyUsersScript),
                eq(List.of(TicketRedisKeys.readyKey("1"))),
                eq(TicketRedisKeys.queueUserPrefix("1")),
                eq(TicketRedisKeys.dedupKeyPrefix("1")),
                anyString(),
                eq("77")
        );
    }

    @Test
    void expireActiveUsers_배치크기가_0이하면_1로_보정한다() {
        ReflectionTestUtils.setField(sut, "expireBatchSize", 0);

        sut.expireActiveUsers("1");

        verify(redisTemplate).execute(
                eq(expireActiveUsersScript),
                eq(List.of(TicketRedisKeys.activeKey("1"))),
                eq(TicketRedisKeys.queueUserPrefix("1")),
                eq(TicketRedisKeys.dedupKeyPrefix("1")),
                anyString(),
                eq("1")
        );
    }
}
