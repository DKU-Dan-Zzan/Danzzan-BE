package com.danzzan.domain.ticket.scheduler;

import com.danzzan.domain.event.model.entity.FestivalEvent;
import com.danzzan.domain.event.model.entity.TicketingStatus;
import com.danzzan.domain.event.repository.FestivalEventRepository;
import com.danzzan.domain.event.service.EventOpenService;
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

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventAutoOpenSchedulerTest {

    @Mock
    private FestivalEventRepository eventRepository;

    @Mock
    private EventOpenService eventOpenService;

    @Mock
    private TicketInitService ticketInitService;

    @Mock
    private UserTicketRepository userTicketRepository;

    private EventAutoOpenScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new EventAutoOpenScheduler(eventRepository, eventOpenService, ticketInitService, userTicketRepository);
    }

    @Test
    void recoverMissingStock_stock복구가_성공하면_발급유저를_redis로_동기화한다() {
        FestivalEvent event = event(10L, 100, TicketingStatus.OPEN);
        List<Long> issuedUsers = List.of(1L, 2L, 3L);
        when(eventRepository.findAllByTicketingStatus(TicketingStatus.OPEN)).thenReturn(List.of(event));
        when(userTicketRepository.countByEventIdAndStatusIn(
                eq(10L),
                eq(List.of(com.danzzan.domain.ticket.model.entity.TicketStatus.CONFIRMED,
                        com.danzzan.domain.ticket.model.entity.TicketStatus.ISSUED,
                        com.danzzan.domain.ticket.model.entity.TicketStatus.CANCELLED_WITHDRAWAL))
        )).thenReturn(30L);
        when(ticketInitService.restoreStockIfMissing("10", 70L)).thenReturn(true);
        when(userTicketRepository.findUserIdsByEventIdAndStatusIn(
                eq(10L),
                eq(List.of(com.danzzan.domain.ticket.model.entity.TicketStatus.CONFIRMED,
                        com.danzzan.domain.ticket.model.entity.TicketStatus.ISSUED))
        )).thenReturn(issuedUsers);
        when(ticketInitService.syncIssuedUsers("10", issuedUsers)).thenReturn(3L);

        scheduler.recoverMissingStock();

        verify(ticketInitService).setEventStatus("10", TicketingStatus.OPEN);
        verify(ticketInitService).restoreStockIfMissing("10", 70L);
        verify(userTicketRepository).findUserIdsByEventIdAndStatusIn(
                eq(10L),
                eq(List.of(com.danzzan.domain.ticket.model.entity.TicketStatus.CONFIRMED,
                        com.danzzan.domain.ticket.model.entity.TicketStatus.ISSUED))
        );
        verify(ticketInitService).syncIssuedUsers("10", issuedUsers);
    }

    @Test
    void recoverMissingStock_stock키가_이미_있으면_동기화를_건너뛴다() {
        FestivalEvent event = event(10L, 100, TicketingStatus.OPEN);
        when(eventRepository.findAllByTicketingStatus(TicketingStatus.OPEN)).thenReturn(List.of(event));
        when(userTicketRepository.countByEventIdAndStatusIn(
                eq(10L),
                eq(List.of(com.danzzan.domain.ticket.model.entity.TicketStatus.CONFIRMED,
                        com.danzzan.domain.ticket.model.entity.TicketStatus.ISSUED,
                        com.danzzan.domain.ticket.model.entity.TicketStatus.CANCELLED_WITHDRAWAL))
        )).thenReturn(30L);
        when(ticketInitService.restoreStockIfMissing("10", 70L)).thenReturn(false);

        scheduler.recoverMissingStock();

        verify(ticketInitService).setEventStatus("10", TicketingStatus.OPEN);
        verify(ticketInitService).restoreStockIfMissing("10", 70L);
        verify(userTicketRepository, never()).findUserIdsByEventIdAndStatusIn(eq(10L), anyList());
        verify(ticketInitService, never()).syncIssuedUsers(anyString(), anyList());
    }

    @Test
    void syncClosedEventStatuses_CLOSED_이벤트_상태를_redis에_기록한다() {
        FestivalEvent closedA = event(10L, 100, TicketingStatus.CLOSED);
        FestivalEvent closedB = event(11L, 200, TicketingStatus.CLOSED);
        when(eventRepository.findAllByTicketingStatus(TicketingStatus.CLOSED))
                .thenReturn(List.of(closedA, closedB));

        scheduler.syncClosedEventStatuses();

        verify(ticketInitService).setEventStatus("10", TicketingStatus.CLOSED);
        verify(ticketInitService).setEventStatus("11", TicketingStatus.CLOSED);
        verify(userTicketRepository, never()).countByEventIdAndStatusIn(eq(10L), anyList());
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
