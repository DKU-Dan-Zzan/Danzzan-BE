package com.danzzan.domain.event.service;

import com.danzzan.domain.event.model.entity.FestivalEvent;
import com.danzzan.domain.event.model.entity.TicketingStatus;
import com.danzzan.domain.event.repository.FestivalEventRepository;
import com.danzzan.domain.ticket.repository.UserTicketRepository;
import com.danzzan.domain.ticket.service.TicketInitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventOpenServiceImplTest {

    @Mock
    private FestivalEventRepository eventRepository;

    @Mock
    private TicketInitService ticketInitService;

    @Mock
    private UserTicketRepository userTicketRepository;

    private EventOpenServiceImpl eventOpenService;

    @BeforeEach
    void setUp() {
        eventOpenService = new EventOpenServiceImpl(eventRepository, ticketInitService, userTicketRepository);
    }

    @Test
    void openNow_OPEN전환이_성공하면_초기화후_발급유저를_동기화한다() {
        FestivalEvent event = event(1L, 100, TicketingStatus.READY);
        List<Long> issuedUsers = List.of(7L, 9L);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(eventRepository.openIfReady(1L)).thenReturn(1);
        when(userTicketRepository.findUserIdsByEventIdAndStatusIn(
                eq(1L),
                eq(List.of(com.danzzan.domain.ticket.model.entity.TicketStatus.CONFIRMED,
                        com.danzzan.domain.ticket.model.entity.TicketStatus.ISSUED))
        )).thenReturn(issuedUsers);
        when(ticketInitService.syncIssuedUsers("1", issuedUsers)).thenReturn(2L);

        boolean opened = eventOpenService.openNow(1L);

        assertThat(opened).isTrue();
        verify(ticketInitService).initStock("1", 100L);
        verify(ticketInitService).setEventStatus("1", TicketingStatus.OPEN);
        verify(userTicketRepository).findUserIdsByEventIdAndStatusIn(
                eq(1L),
                eq(List.of(com.danzzan.domain.ticket.model.entity.TicketStatus.CONFIRMED,
                        com.danzzan.domain.ticket.model.entity.TicketStatus.ISSUED))
        );
        verify(ticketInitService).syncIssuedUsers("1", issuedUsers);
    }

    @Test
    void openNow_OPEN전환을_못하면_초기화와_동기화를_생략한다() {
        FestivalEvent event = event(1L, 100, TicketingStatus.OPEN);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(eventRepository.openIfReady(1L)).thenReturn(0);

        boolean opened = eventOpenService.openNow(1L);

        assertThat(opened).isFalse();
        verify(ticketInitService, never()).initStock("1", 100L);
        verify(ticketInitService, never()).setEventStatus(anyString(), org.mockito.ArgumentMatchers.any());
        verify(userTicketRepository, never()).findUserIdsByEventIdAndStatusIn(eq(1L), anyList());
        verify(ticketInitService, never()).syncIssuedUsers(anyString(), anyList());
    }

    private FestivalEvent event(Long id, int totalCapacity, TicketingStatus status) {
        FestivalEvent event = FestivalEvent.builder()
                .title("test")
                .eventDate(LocalDate.of(2026, 4, 17))
                .ticketingStartTime(LocalDateTime.of(2026, 4, 17, 18, 0))
                .ticketingStatus(status)
                .totalCapacity(totalCapacity)
                .build();
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }
}
