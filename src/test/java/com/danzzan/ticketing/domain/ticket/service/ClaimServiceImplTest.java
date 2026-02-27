package com.danzzan.ticketing.domain.ticket.service;

import com.danzzan.ticketing.domain.ticket.redis.TicketRedisKeys;
import com.danzzan.ticketing.domain.ticket.redis.TicketRequestStatus;
import com.danzzan.ticketing.domain.ticket.service.model.ClaimResult;
import com.danzzan.ticketing.domain.ticket.service.support.ClaimLuaProtocol;
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

        mockLuaResult(List.of(ClaimLuaProtocol.CODE_ALREADY, -1L));

        ClaimResult result = claimService.claim(eventId, userId);

        assertThat(result.status()).isEqualTo(TicketRequestStatus.ALREADY);
        assertThat(result.remaining()).isNull();
    }

    @Test
    void mapsSoldOutFromLuaResultAndKeepsRemainingNull() {
        String eventId = "festival-day1";
        String userId = "32221902";

        mockLuaResult(List.of(ClaimLuaProtocol.CODE_SOLD_OUT, -1L));

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

        mockLuaResult(List.of(ClaimLuaProtocol.CODE_SUCCESS, 42L));

        ClaimResult result = claimService.claim(eventId, userId);

        assertThat(result.status()).isEqualTo(TicketRequestStatus.SUCCESS);
        assertThat(result.remaining()).isEqualTo(42L);
        verify(stringRedisTemplate).execute(
                eq(claimV2Script),
                eq(List.of(userKey, stockKey, statusKey)),
                eq(TicketRequestStatus.ALREADY.name()),
                eq(TicketRequestStatus.SOLD_OUT.name()),
                eq(TicketRequestStatus.SUCCESS.name()),
                eq(ClaimLuaProtocol.USER_CLAIMED_VALUE),
                eq(ClaimLuaProtocol.CODE_ALREADY_ARG),
                eq(ClaimLuaProtocol.CODE_SOLD_OUT_ARG),
                eq(ClaimLuaProtocol.CODE_SUCCESS_ARG)
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
        mockLuaResult(Arrays.asList(ClaimLuaProtocol.CODE_SUCCESS, null));

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
