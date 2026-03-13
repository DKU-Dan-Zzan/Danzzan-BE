package com.danzzan.domain.ticket.scheduler;

import com.danzzan.domain.event.model.entity.FestivalEvent;
import com.danzzan.domain.event.model.entity.TicketingStatus;
import com.danzzan.domain.event.repository.FestivalEventRepository;
import com.danzzan.domain.ticket.redis.TicketRedisKeys;
import com.danzzan.domain.ticket.service.SlotService;
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
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TicketAdmissionSchedulerTest {

    @Mock StringRedisTemplate redis;
    @Mock ZSetOperations<String, String> zOps;
    @Mock ValueOperations<String, String> vOps;
    @Mock FestivalEventRepository eventRepo;
    @Mock SlotService slotService;

    TicketAdmissionScheduler sut;

    static final String EVENT_ID  = "1";
    static final String QUEUE_KEY = TicketRedisKeys.queueKey(EVENT_ID);
    static final String STOCK_KEY = TicketRedisKeys.stockKey(EVENT_ID);

    @BeforeEach
    void setUp() {
        doReturn(zOps).when(redis).opsForZSet();
        doReturn(vOps).when(redis).opsForValue();

        sut = new TicketAdmissionScheduler(redis, slotService, eventRepo);
        ReflectionTestUtils.setField(sut, "maxConcurrentSlots", 100);
        ReflectionTestUtils.setField(sut, "gateTtlSeconds", 300L);
    }

    /** mock 대신 실 객체 사용 — JPA 엔티티 바이트코드 증강이 Mockito 상태를 오염시키는 문제 방지 */
    private FestivalEvent openEvent(long id) {
        FestivalEvent e = FestivalEvent.builder()
                .title("test")
                .eventDate(LocalDate.now())
                .ticketingStartTime(LocalDateTime.now())
                .ticketingStatus(TicketingStatus.OPEN)
                .totalCapacity(1000)
                .build();
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }

    private TypedTuple<String> tuple(String value) {
        return new TypedTuple<>() {
            @Override public String getValue() { return value; }
            @Override public Double getScore() { return 0.0; }
            @Override public int compareTo(TypedTuple<String> o) { return 0; }
        };
    }

    // ──────────────────────────────────────────────────────────────
    // 정상 케이스
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("여유 슬롯 2개일 때 popMin(2) 호출 후 gate 키 발급")
    void processQueue_admitsUpToFreeSlots() {
        when(eventRepo.findAllByTicketingStatus(TicketingStatus.OPEN))
                .thenReturn(List.of(openEvent(1L)));
        when(slotService.activeSlotCount(EVENT_ID)).thenReturn(98L); // freeSlots=2
        when(vOps.get(STOCK_KEY)).thenReturn("50");
        when(zOps.popMin(QUEUE_KEY, 2L))
                .thenReturn(Set.of(tuple("userA"), tuple("userB")));
        when(slotService.acquireSlot(eq(EVENT_ID), anyString())).thenReturn(true);

        sut.admitFromQueue();

        verify(zOps).popMin(QUEUE_KEY, 2L);
        verify(vOps, times(2)).set(anyString(), eq("1"), any());
    }

    @Test
    @DisplayName("슬롯이 꽉 찼을 때(freeSlots=0) popMin 호출 안 함")
    void processQueue_skipsWhenNoFreeSlots() {
        when(eventRepo.findAllByTicketingStatus(TicketingStatus.OPEN))
                .thenReturn(List.of(openEvent(1L)));
        when(slotService.activeSlotCount(EVENT_ID)).thenReturn(100L);

        sut.admitFromQueue();

        verify(zOps, never()).popMin(any(), anyLong());
    }

    @Test
    @DisplayName("stock=0이면 popMin 호출 안 함")
    void processQueue_skipsWhenStockEmpty() {
        when(eventRepo.findAllByTicketingStatus(TicketingStatus.OPEN))
                .thenReturn(List.of(openEvent(1L)));
        when(slotService.activeSlotCount(EVENT_ID)).thenReturn(0L);
        when(vOps.get(STOCK_KEY)).thenReturn("0");

        sut.admitFromQueue();

        verify(zOps, never()).popMin(any(), anyLong());
    }

    @Test
    @DisplayName("acquireSlot 실패한 userId는 재입대(addIfAbsent)")
    void processQueue_requeueOnAcquireFailure() {
        when(eventRepo.findAllByTicketingStatus(TicketingStatus.OPEN))
                .thenReturn(List.of(openEvent(1L)));
        when(slotService.activeSlotCount(EVENT_ID)).thenReturn(99L);
        when(vOps.get(STOCK_KEY)).thenReturn("10");
        when(zOps.popMin(QUEUE_KEY, 1L)).thenReturn(Set.of(tuple("userX")));
        when(slotService.acquireSlot(EVENT_ID, "userX")).thenReturn(false);

        sut.admitFromQueue();

        verify(vOps, never()).set(anyString(), eq("1"), any());
        verify(zOps).addIfAbsent(eq(QUEUE_KEY), eq("userX"), anyDouble());
    }

    @Test
    @DisplayName("toAdmit = min(freeSlots=100, stock=5, CEILING=100) = 5")
    void processQueue_toAdmitIsMinOfThree() {
        when(eventRepo.findAllByTicketingStatus(TicketingStatus.OPEN))
                .thenReturn(List.of(openEvent(1L)));
        when(slotService.activeSlotCount(EVENT_ID)).thenReturn(0L);
        when(vOps.get(STOCK_KEY)).thenReturn("5");
        when(zOps.popMin(eq(QUEUE_KEY), anyLong())).thenReturn(Set.of());

        sut.admitFromQueue();

        verify(zOps).popMin(QUEUE_KEY, 5L);
    }

    @Test
    @DisplayName("OPEN 이벤트 없으면 Redis 호출 없음")
    void processQueue_noOpenEvents_doesNothing() {
        when(eventRepo.findAllByTicketingStatus(TicketingStatus.OPEN)).thenReturn(List.of());

        sut.admitFromQueue();

        verify(redis, never()).opsForZSet();
        verify(redis, never()).opsForValue();
        verifyNoInteractions(slotService);
    }

    @Test
    @DisplayName("stock null이면 0으로 파싱 → popMin 안 함")
    void processQueue_nullStockSkips() {
        when(eventRepo.findAllByTicketingStatus(TicketingStatus.OPEN))
                .thenReturn(List.of(openEvent(1L)));
        when(slotService.activeSlotCount(EVENT_ID)).thenReturn(0L);
        when(vOps.get(STOCK_KEY)).thenReturn(null);

        sut.admitFromQueue();

        verify(zOps, never()).popMin(any(), anyLong());
    }

    // ──────────────────────────────────────────────────────────────
    // gate 키 TTL 검증
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("gate 키 TTL = 300초로 설정")
    void processQueue_gateKeySetWith300sTtl() {
        when(eventRepo.findAllByTicketingStatus(TicketingStatus.OPEN))
                .thenReturn(List.of(openEvent(1L)));
        when(slotService.activeSlotCount(EVENT_ID)).thenReturn(99L);
        when(vOps.get(STOCK_KEY)).thenReturn("10");
        when(zOps.popMin(QUEUE_KEY, 1L)).thenReturn(Set.of(tuple("userZ")));
        when(slotService.acquireSlot(EVENT_ID, "userZ")).thenReturn(true);

        sut.admitFromQueue();

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(vOps).set(anyString(), eq("1"), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue().getSeconds()).isEqualTo(300L);
    }
}
