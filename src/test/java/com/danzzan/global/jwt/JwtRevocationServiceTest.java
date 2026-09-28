package com.danzzan.global.jwt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtRevocationServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private CommittedUserVersionReader committedUserVersionReader;

    @Test
    void missingCachePublishesCurrentDatabaseVersionInsteadOfDelayedCallerVersion() {
        JwtRevocationService service = service();
        when(committedUserVersionReader.findActiveTokenVersion(1L)).thenReturn(Optional.of(2));

        service.publishUserVersionAfterCommit(1L, 0);

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(redisTemplate).execute(any(DefaultRedisScript.class), anyList(), args.capture());
        assertThat(args.getValue()[0]).isEqualTo("2");
    }

    @Test
    void delayedVersionOneCallbackCannotReplaceCurrentDatabaseVersionTwo() {
        JwtRevocationService service = service();
        when(committedUserVersionReader.findActiveTokenVersion(1L)).thenReturn(Optional.of(2));

        service.publishUserVersionAfterCommit(1L, 1);

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(redisTemplate).execute(any(DefaultRedisScript.class), anyList(), args.capture());
        assertThat(args.getValue()[0]).isEqualTo("2");
    }

    @Test
    void redisPublicationFailureDoesNotThrowAfterCommittedWrite() {
        JwtRevocationService service = service();
        when(committedUserVersionReader.findActiveTokenVersion(1L)).thenReturn(Optional.of(2));
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("redis unavailable"));

        assertThatCode(() -> service.publishUserVersionAfterCommit(1L, 0)).doesNotThrowAnyException();
    }

    @Test
    void afterCommitSideEffectFailureDoesNotThrow() {
        JwtRevocationService service = service();

        assertThatCode(() -> service.runAfterCommit(1L, "withdraw", () -> {
            throw new IllegalStateException("redis unavailable");
        })).doesNotThrowAnyException();
    }

    private JwtRevocationService service() {
        JwtRevocationService service = new JwtRevocationService(redisTemplate, jwtTokenProvider, committedUserVersionReader);
        ReflectionTestUtils.setField(service, "refreshTokenExpirationMs", 60_000L);
        return service;
    }
}
