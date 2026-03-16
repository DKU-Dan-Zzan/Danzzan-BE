package com.danzzan.domain.ticket.scheduler;

import com.danzzan.domain.event.model.entity.FestivalEvent;
import com.danzzan.domain.event.model.entity.TicketingStatus;
import com.danzzan.domain.event.repository.FestivalEventRepository;
import com.danzzan.domain.ticket.redis.TicketRedisKeys;
import com.danzzan.domain.ticket.service.QueueStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketAdmissionSchedulerTest {

    @Mock FestivalEventRepository eventRepo;
    @Mock QueueStateService queueStateService;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;

    TicketAdmissionScheduler sut;

    static final String EVENT_ID = "1";

    @BeforeEach
    void setUp() {
        sut = new TicketAdmissionScheduler(queueStateService, eventRepo, redisTemplate);
        ReflectionTestUtils.setField(sut, "maxConcurrent", 100);
        ReflectionTestUtils.setField(sut, "readyTtlSeconds", 180L);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private FestivalEvent openEvent() {
        FestivalEvent e = mock(FestivalEvent.class);
        when(e.getId()).thenReturn(1L);
        return e;
    }

    @Test
    void 승격할_사용자가_없으면_중단한다() {
        FestivalEvent event = openEvent();
        when(eventRepo.findAllByTicketingStatus(TicketingStatus.OPEN))
                .thenReturn(List.of(event));
        when(queueStateService.expireReadyUsers(EVENT_ID)).thenReturn(0);
        when(queueStateService.expireActiveUsers(EVENT_ID)).thenReturn(0);
        when(queueStateService.admitNextWaitingUser(eq(EVENT_ID), anyLong(), eq(100))).thenReturn(false);

        sut.admitFromQueue();

        verify(queueStateService).admitNextWaitingUser(eq(EVENT_ID), anyLong(), eq(100));
    }

    @Test
    void 배치_상한까지만_READY_승격을_시도한다() {
        FestivalEvent event = openEvent();
        when(eventRepo.findAllByTicketingStatus(TicketingStatus.OPEN))
                .thenReturn(List.of(event));
        when(queueStateService.expireReadyUsers(EVENT_ID)).thenReturn(0);
        when(queueStateService.expireActiveUsers(EVENT_ID)).thenReturn(0);
        when(queueStateService.admitNextWaitingUser(eq(EVENT_ID), anyLong(), eq(100))).thenReturn(true);

        sut.admitFromQueue();

        verify(queueStateService, times(100)).admitNextWaitingUser(eq(EVENT_ID), anyLong(), eq(100));
    }

    @Test
    void 중간에_승격대상이_없어지면_반복을_멈춘다() {
        FestivalEvent event = openEvent();
        when(eventRepo.findAllByTicketingStatus(TicketingStatus.OPEN))
                .thenReturn(List.of(event));
        when(queueStateService.expireReadyUsers(EVENT_ID)).thenReturn(0);
        when(queueStateService.expireActiveUsers(EVENT_ID)).thenReturn(0);
        when(queueStateService.admitNextWaitingUser(eq(EVENT_ID), anyLong(), eq(100)))
                .thenReturn(true, true, false);

        sut.admitFromQueue();

        verify(queueStateService, times(3)).admitNextWaitingUser(eq(EVENT_ID), anyLong(), eq(100));
    }

    @Test
    void 만료된_READY와_ACTIVE를_먼저_정리한다() {
        FestivalEvent event = openEvent();
        when(eventRepo.findAllByTicketingStatus(TicketingStatus.OPEN))
                .thenReturn(List.of(event));
        when(queueStateService.expireReadyUsers(EVENT_ID)).thenReturn(3);
        when(queueStateService.expireActiveUsers(EVENT_ID)).thenReturn(2);
        when(queueStateService.admitNextWaitingUser(eq(EVENT_ID), anyLong(), eq(100))).thenReturn(false);

        sut.admitFromQueue();

        verify(queueStateService).expireReadyUsers(EVENT_ID);
        verify(queueStateService).expireActiveUsers(EVENT_ID);
    }

    @Test
    void CLOSED_이벤트에서는_남은_대기열을_취소한다() {
        FestivalEvent closedEvent = mock(FestivalEvent.class);
        when(closedEvent.getId()).thenReturn(2L);
        when(eventRepo.findAllByTicketingStatus(TicketingStatus.OPEN)).thenReturn(List.of());
        when(eventRepo.findAllByTicketingStatus(TicketingStatus.CLOSED)).thenReturn(List.of(closedEvent));
        when(redisTemplate.hasKey(TicketRedisKeys.closedCleanupKey("2"))).thenReturn(false);
        when(queueStateService.cancelWaitingQueue("2")).thenReturn(4);

        sut.admitFromQueue();

        verify(queueStateService).cancelWaitingQueue("2");
        verify(valueOperations).set(TicketRedisKeys.closedCleanupKey("2"), "1");
    }

    @Test
    void CLOSED_정리마커가_있으면_반복취소를_건너뛴다() {
        FestivalEvent closedEvent = mock(FestivalEvent.class);
        when(closedEvent.getId()).thenReturn(2L);
        when(eventRepo.findAllByTicketingStatus(TicketingStatus.OPEN)).thenReturn(List.of());
        when(eventRepo.findAllByTicketingStatus(TicketingStatus.CLOSED)).thenReturn(List.of(closedEvent));
        when(redisTemplate.hasKey(TicketRedisKeys.closedCleanupKey("2"))).thenReturn(true);

        sut.admitFromQueue();

        verify(queueStateService, never()).cancelWaitingQueue("2");
        verify(valueOperations, never()).set(anyString(), anyString());
    }
}
