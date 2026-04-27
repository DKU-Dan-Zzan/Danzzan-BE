package com.danzzan.domain.ticket.service;

import com.danzzan.domain.event.model.entity.TicketingStatus;
import com.danzzan.domain.ticket.redis.TicketRedisKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketInitServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private TicketInitServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TicketInitServiceImpl(redisTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void syncIssuedUsers_유저목록을_userKey로_multiSet한다() {
        long synced = service.syncIssuedUsers("10", List.of(1L, 2L));

        ArgumentCaptor<Map<String, String>> mapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(valueOperations).multiSet(mapCaptor.capture());
        Map<String, String> saved = mapCaptor.getValue();
        assertThat(synced).isEqualTo(2L);
        assertThat(saved).containsEntry(TicketRedisKeys.userKey("10", "1"), "1");
        assertThat(saved).containsEntry(TicketRedisKeys.userKey("10", "2"), "1");
    }

    @Test
    void syncIssuedUsers_동기화대상이_없으면_아무작업도_하지않는다() {
        long synced = service.syncIssuedUsers("10", List.of());

        assertThat(synced).isZero();
        verify(valueOperations, never()).multiSet(anyMap());
    }

    @Test
    void setEventStatus_이벤트상태를_redis에_저장한다() {
        service.setEventStatus("10", TicketingStatus.OPEN);

        verify(valueOperations).set(TicketRedisKeys.eventStatusKey("10"), "OPEN");
    }
}
