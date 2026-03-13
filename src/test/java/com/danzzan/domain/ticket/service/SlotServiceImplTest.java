package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.redis.TicketRedisKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SlotServiceImplTest {

    @Mock StringRedisTemplate redis;
    @Mock ZSetOperations<String, String> zOps;

    SlotServiceImpl sut;

    static final String EVENT_ID   = "42";
    static final String USER_ID    = "7";
    static final String ACTIVE_KEY = TicketRedisKeys.activeKey(EVENT_ID);
    static final String GATE_KEY   = TicketRedisKeys.gateUserKey(EVENT_ID, USER_ID);

    @BeforeEach
    void setUp() {
        doReturn(zOps).when(redis).opsForZSet();
        sut = new SlotServiceImpl(redis);
        ReflectionTestUtils.setField(sut, "maxConcurrentSlots", 3);
        ReflectionTestUtils.setField(sut, "gateTtlSeconds", 300L);
    }

    @Test
    @DisplayName("슬롯 여유가 있으면 만료 정리 후 active ZSet에 추가하고 true 반환")
    void acquireSlot_addsActiveSlotWhenCapacityAvailable() {
        when(zOps.removeRangeByScore(eq(ACTIVE_KEY), eq(0d), anyDouble())).thenReturn(0L);
        when(zOps.zCard(ACTIVE_KEY)).thenReturn(2L);
        when(zOps.add(eq(ACTIVE_KEY), eq(USER_ID), anyDouble())).thenReturn(true);

        long before = System.currentTimeMillis();
        boolean result = sut.acquireSlot(EVENT_ID, USER_ID);
        long after = System.currentTimeMillis();

        assertThat(result).isTrue();

        ArgumentCaptor<Double> expiryCaptor = ArgumentCaptor.forClass(Double.class);
        verify(zOps).removeRangeByScore(eq(ACTIVE_KEY), eq(0d), anyDouble());
        verify(zOps).zCard(ACTIVE_KEY);
        verify(zOps).add(eq(ACTIVE_KEY), eq(USER_ID), expiryCaptor.capture());
        assertThat(expiryCaptor.getValue().longValue()).isBetween(before + 300_000L, after + 300_000L);
    }

    // ──────────────────────────────────────────────
    // acquireSlot — 슬롯 부족
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("슬롯이 꽉 차 있으면 false 반환하고 add를 호출하지 않음")
    void acquireSlot_returnsFalseWhenCapacityExceeded() {
        when(zOps.removeRangeByScore(eq(ACTIVE_KEY), eq(0d), anyDouble())).thenReturn(0L);
        when(zOps.zCard(ACTIVE_KEY)).thenReturn(3L);

        boolean result = sut.acquireSlot(EVENT_ID, USER_ID);

        assertThat(result).isFalse();
        verify(zOps, never()).add(anyString(), anyString(), anyDouble());
    }

    // ──────────────────────────────────────────────
    // releaseSlot
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("releaseSlot → ZREM + gateUserKey DELETE 호출")
    void releaseSlot_removesFromZSetAndDeletesGateKey() {
        sut.releaseSlot(EVENT_ID, USER_ID);

        verify(zOps).remove(ACTIVE_KEY, USER_ID);
        verify(redis).delete(GATE_KEY);
    }

    @Test
    @DisplayName("releaseSlot Redis 예외 → 예외 전파 없이 로그만")
    void releaseSlot_exceptionSuppressed() {
        doThrow(new RuntimeException("redis down")).when(zOps).remove(any(), (Object) any());

        sut.releaseSlot(EVENT_ID, USER_ID);
    }

    // ──────────────────────────────────────────────
    // activeSlotCount
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("activeSlotCount → 만료 정리 후 ZCARD 반환")
    void activeSlotCount_returnsCardAfterClean() {
        when(zOps.removeRangeByScore(eq(ACTIVE_KEY), eq(0d), anyDouble())).thenReturn(0L);
        when(zOps.zCard(ACTIVE_KEY)).thenReturn(2L);

        assertThat(sut.activeSlotCount(EVENT_ID)).isEqualTo(2L);
    }

    @Test
    @DisplayName("activeSlotCount ZCARD null → 0 반환")
    void activeSlotCount_whenNull_returnsZero() {
        when(zOps.removeRangeByScore(any(), anyDouble(), anyDouble())).thenReturn(0L);
        when(zOps.zCard(ACTIVE_KEY)).thenReturn(null);

        assertThat(sut.activeSlotCount(EVENT_ID)).isEqualTo(0L);
    }
}
