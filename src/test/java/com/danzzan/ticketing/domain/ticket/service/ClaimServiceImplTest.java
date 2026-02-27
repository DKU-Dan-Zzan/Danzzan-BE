package com.danzzan.ticketing.domain.ticket.service;

import com.danzzan.ticketing.domain.ticket.redis.TicketRedisKeys;
import com.danzzan.ticketing.domain.ticket.redis.TicketRequestStatus;
import com.danzzan.ticketing.domain.ticket.service.model.ClaimResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimServiceImplTest {

    private static final long LUA_CODE_ALREADY = 1L;
    private static final long LUA_CODE_SOLD_OUT = 2L;
    private static final long LUA_CODE_SUCCESS = 3L;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private RedisScript<List> claimV2Script;

    @InjectMocks
    private ClaimServiceImpl claimService;

    @Test
    void mapsAlreadyFromLuaResult() {
        String eventId = "festival-day1";
        String userId = "32221902";

        mockLuaResult(List.of(LUA_CODE_ALREADY, -1L));

        ClaimResult result = claimService.claim(eventId, userId);

        assertThat(result.status()).isEqualTo(TicketRequestStatus.ALREADY);
        assertThat(result.remaining()).isNull();
    }

    @Test
    void mapsSoldOutFromLuaResultAndKeepsRemainingNull() {
        String eventId = "festival-day1";
        String userId = "32221902";

        mockLuaResult(List.of(LUA_CODE_SOLD_OUT, -1L));

        ClaimResult result = claimService.claim(eventId, userId);

        assertThat(result.status()).isEqualTo(TicketRequestStatus.SOLD_OUT);
        assertThat(result.remaining()).isNull();
    }

    @Test
    void mapsSuccessFromLuaResultAndUsesRemaining() {
        String eventId = "festival-day1";
        String userId = "32221902";
        String userKey = TicketRedisKeys.userKey(eventId, userId);
        String statusKey = TicketRedisKeys.statusKey(eventId, userId);
        String stockKey = TicketRedisKeys.stockKey(eventId);

        mockLuaResult(List.of(LUA_CODE_SUCCESS, 42L));

        ClaimResult result = claimService.claim(eventId, userId);

        assertThat(result.status()).isEqualTo(TicketRequestStatus.SUCCESS);
        assertThat(result.remaining()).isEqualTo(42L);
        verify(stringRedisTemplate).execute(
                eq(claimV2Script),
                eq(List.of(userKey, stockKey, statusKey)),
                eq(TicketRequestStatus.ALREADY.name()),
                eq(TicketRequestStatus.SOLD_OUT.name()),
                eq(TicketRequestStatus.SUCCESS.name()),
                eq("1"),
                eq(String.valueOf(LUA_CODE_ALREADY)),
                eq(String.valueOf(LUA_CODE_SOLD_OUT)),
                eq(String.valueOf(LUA_CODE_SUCCESS))
        );
    }

    @Test
    void throwsWhenLuaResultIsNull() {
        mockLuaResult(null);

        assertThatThrownBy(() -> claimService.claim("festival-day1", "32221902"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("[code, remaining]");
    }

    @Test
    void throwsWhenLuaResultContainsUnknownCode() {
        mockLuaResult(List.of(999L, 10L));

        assertThatThrownBy(() -> claimService.claim("festival-day1", "32221902"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unexpected claim lua code");
    }

    @Test
    void throwsWhenLuaSuccessHasNoRemaining() {
        mockLuaResult(Arrays.asList(LUA_CODE_SUCCESS, null));

        assertThatThrownBy(() -> claimService.claim("festival-day1", "32221902"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires remaining");
    }

    @Test
    void throwsWhenLuaCodeIsNotNumberLike() {
        mockLuaResult(List.of("unknown", 1L));

        assertThatThrownBy(() -> claimService.claim("festival-day1", "32221902"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("parseable as long");
    }

    private void mockLuaResult(List<?> luaResult) {
        when(stringRedisTemplate.execute(
                eq(claimV2Script),
                anyList(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(luaResult);
    }
}
