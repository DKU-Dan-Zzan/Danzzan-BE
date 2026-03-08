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
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SlotServiceImplTest {

    @Mock StringRedisTemplate redis;
    @Mock ZSetOperations<String, String> zOps;
    @Mock RedisScript<Long> acquireSlotScript;

    SlotServiceImpl sut;

    static final String EVENT_ID   = "42";
    static final String USER_ID    = "7";
    static final String ACTIVE_KEY = TicketRedisKeys.activeKey(EVENT_ID);
    static final String GATE_KEY   = TicketRedisKeys.gateUserKey(EVENT_ID, USER_ID);

    @BeforeEach
    void setUp() {
        doReturn(zOps).when(redis).opsForZSet();
        sut = new SlotServiceImpl(redis, acquireSlotScript);
        ReflectionTestUtils.setField(sut, "maxConcurrentSlots", 3);
        ReflectionTestUtils.setField(sut, "gateTtlSeconds", 300L);
    }

    // ──────────────────────────────────────────────
    // acquireSlot — Lua 위임 검증
    // ──────────────────────────────────────────────

    // acquireSlot() 은 execute(script, keys, nowMs, maxSlots, userId, expiryMs) 4개 vararg 전달
    private void stubExecute(long returnVal) {
        doReturn(returnVal).when(redis)
                .execute(eq(acquireSlotScript), anyList(),
                        any(), any(), any(), any()); // vararg 4개 각각 any()
    }

    @Test
    @DisplayName("슬롯 여유 있을 때 Lua가 1 반환 → acquireSlot true")
    void acquireSlot_luaReturnsOne_returnsTrue() {
        stubExecute(1L);

        boolean result = sut.acquireSlot(EVENT_ID, USER_ID);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("슬롯 꽉 찼을 때 Lua가 0 반환 → acquireSlot false")
    void acquireSlot_luaReturnsZero_returnsFalse() {
        stubExecute(0L);

        boolean result = sut.acquireSlot(EVENT_ID, USER_ID);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("acquireSlot 시 KEYS=[activeKey], ARGV=[nowMs, maxSlots, userId, expiryMs] 순서로 Lua 호출")
    void acquireSlot_luaCalledWithCorrectArgs() {
        stubExecute(1L);

        long before = System.currentTimeMillis();
        sut.acquireSlot(EVENT_ID, USER_ID);
        long after = System.currentTimeMillis();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Object[]> argvCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(redis).execute(eq(acquireSlotScript), keysCaptor.capture(), argvCaptor.capture());

        // KEYS 검증
        assertThat(keysCaptor.getValue()).containsExactly(ACTIVE_KEY);

        // ARGV 검증
        Object[] argv = argvCaptor.getValue();
        long nowMs    = Long.parseLong((String) argv[0]);
        long maxSlots = Long.parseLong((String) argv[1]);
        String uid    = (String) argv[2];
        long expiryMs = Long.parseLong((String) argv[3]);

        assertThat(nowMs).isBetween(before, after);
        assertThat(maxSlots).isEqualTo(3L);
        assertThat(uid).isEqualTo(USER_ID);
        assertThat(expiryMs).isBetween(before + 300_000L, after + 300_000L);
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

        sut.releaseSlot(EVENT_ID, USER_ID); // 예외가 밖으로 터지지 않아야 함
    }

    // ──────────────────────────────────────────────
    // activeSlotCount — 여전히 직접 Redis 명령
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
