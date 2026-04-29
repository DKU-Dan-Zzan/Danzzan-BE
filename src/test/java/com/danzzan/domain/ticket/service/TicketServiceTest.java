package com.danzzan.domain.ticket.service;

import com.danzzan.domain.event.model.entity.FestivalEvent;
import com.danzzan.domain.event.model.entity.TicketingStatus;
import com.danzzan.domain.event.repository.FestivalEventRepository;
import com.danzzan.domain.ticket.redis.TicketRedisKeys;
import com.danzzan.domain.ticket.repository.UserTicketRepository;
import com.danzzan.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private FestivalEventRepository eventRepository;

    @Mock
    private UserTicketRepository ticketRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(eventRepository, ticketRepository, userRepository, redisTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void hasTicket_redisUserKey가_존재하면_true를_반환한다() {
        when(redisTemplate.hasKey(TicketRedisKeys.userKey("10", "1"))).thenReturn(true);

        boolean result = ticketService.hasTicket(1L, 10L);

        assertThat(result).isTrue();
        verify(ticketRepository, never()).existsByUserIdAndEventId(anyLong(), anyLong());
    }

    @Test
    void hasTicket_redisUserKey가_없으면_false를_반환한다() {
        when(redisTemplate.hasKey(TicketRedisKeys.userKey("10", "1"))).thenReturn(false);

        boolean result = ticketService.hasTicket(1L, 10L);

        assertThat(result).isFalse();
        verify(ticketRepository, never()).existsByUserIdAndEventId(anyLong(), anyLong());
    }

    @Test
    void getTicketingStatusFromCache_redis에_상태가_있으면_DB를_조회하지_않는다() {
        when(valueOperations.get(TicketRedisKeys.eventStatusKey("10"))).thenReturn("OPEN");

        TicketingStatus status = ticketService.getTicketingStatusFromCache(10L);

        assertThat(status).isEqualTo(TicketingStatus.OPEN);
        verify(eventRepository, never()).findById(anyLong());
    }

    @Test
    void getTicketingStatusFromCache_redis에_상태가_없으면_null을_반환하고_DB를_조회하지_않는다() {
        when(valueOperations.get(TicketRedisKeys.eventStatusKey("10"))).thenReturn(null);

        TicketingStatus status = ticketService.getTicketingStatusFromCache(10L);

        assertThat(status).isNull();
        verify(eventRepository, never()).findById(anyLong());
    }

    @Test
    void getQueueEnterEligibility_multiGet으로_티켓보유와_이벤트상태를_동시조회한다() {
        when(valueOperations.multiGet(List.of(
                TicketRedisKeys.userKey("10", "1"),
                TicketRedisKeys.eventStatusKey("10")
        ))).thenReturn(List.of("1", "OPEN"));

        TicketService.QueueEnterEligibility eligibility = ticketService.getQueueEnterEligibility(1L, 10L);

        assertThat(eligibility.hasTicket()).isTrue();
        assertThat(eligibility.ticketingStatus()).isEqualTo(TicketingStatus.OPEN);
    }

    @Test
    void getQueueEnterEligibility_multiGet결과가_비어있으면_false_null을_반환한다() {
        when(valueOperations.multiGet(List.of(
                TicketRedisKeys.userKey("10", "1"),
                TicketRedisKeys.eventStatusKey("10")
        ))).thenReturn(Arrays.asList(null, null));

        TicketService.QueueEnterEligibility eligibility = ticketService.getQueueEnterEligibility(1L, 10L);

        assertThat(eligibility.hasTicket()).isFalse();
        assertThat(eligibility.ticketingStatus()).isNull();
    }

    private FestivalEvent event(Long id, TicketingStatus status) {
        FestivalEvent event = FestivalEvent.builder()
                .title("event")
                .eventDate(LocalDate.of(2026, 4, 20))
                .ticketingStartTime(LocalDateTime.of(2026, 4, 20, 10, 0))
                .ticketingStatus(status)
                .totalCapacity(100)
                .build();
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }
}
