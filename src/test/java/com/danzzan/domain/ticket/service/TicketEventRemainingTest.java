package com.danzzan.domain.ticket.service;

import com.danzzan.domain.event.model.entity.FestivalEvent;
import com.danzzan.domain.event.model.entity.TicketingStatus;
import com.danzzan.domain.event.repository.FestivalEventRepository;
import com.danzzan.domain.ticket.dto.ResponseTicketEventListDto;
import com.danzzan.domain.ticket.repository.UserTicketRepository;
import com.danzzan.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 오픈 전(READY) 이벤트의 잔여 수량은 전체 수량 그대로여야 한다.
 *
 * 재고 키는 오픈하는 순간 새로 세팅된다. 오픈 전에 재고 키를 읽으면, 같은 id 를 다시 쓴
 * 이벤트가 예전 축제의 남은 수량을 물려받아 "1000장인데 잔여 2998장" 같은 값이 보인다.
 * 로컬에서 실제로 이 상태를 만났다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TicketEventRemainingTest {

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

    @InjectMocks
    private TicketService ticketService;

    @Test
    void 오픈_전_이벤트는_남은_재고_키를_보지_않는다() {
        // 예전 축제가 쓰던 같은 id 의 재고 값이 남아 있는 상황
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.get(anyString())).thenReturn("2998");
        when(eventRepository.findConfiguredEvents()).thenReturn(List.of(event(TicketingStatus.READY, 1000)));

        ResponseTicketEventListDto result = ticketService.getTicketingEvents();

        assertEquals(1000, result.getItems().get(0).getRemainingCount());
        assertEquals("upcoming", result.getItems().get(0).getStatus());
    }

    @Test
    void 오픈한_이벤트는_재고_키를_그대로_쓴다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("120");
        when(eventRepository.findConfiguredEvents()).thenReturn(List.of(event(TicketingStatus.OPEN, 1000)));

        ResponseTicketEventListDto result = ticketService.getTicketingEvents();

        assertEquals(120, result.getItems().get(0).getRemainingCount());
        assertEquals("open", result.getItems().get(0).getStatus());
    }

    @Test
    void 마감한_이벤트는_항상_0이다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.get(anyString())).thenReturn("500");
        when(eventRepository.findConfiguredEvents()).thenReturn(List.of(event(TicketingStatus.CLOSED, 1000)));

        ResponseTicketEventListDto result = ticketService.getTicketingEvents();

        assertEquals(0, result.getItems().get(0).getRemainingCount());
    }

    private FestivalEvent event(TicketingStatus status, int capacity) {
        FestivalEvent event = FestivalEvent.builder()
                .title("2026 DANFESTA 1회차")
                .eventDate(LocalDate.of(2026, 9, 9))
                .ticketingStartTime(LocalDateTime.of(2026, 9, 1, 18, 0))
                .ticketingStatus(status)
                .totalCapacity(capacity)
                .build();
        ReflectionTestUtils.setField(event, "id", 1L);
        return event;
    }
}
