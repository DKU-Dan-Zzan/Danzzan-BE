package com.danzzan.domain.event.service;

import com.danzzan.domain.event.dto.EventStatsResponseDTO;
import com.danzzan.domain.event.model.entity.FestivalEvent;
import com.danzzan.domain.event.model.entity.TicketingStatus;
import com.danzzan.domain.event.repository.FestivalEventRepository;
import com.danzzan.domain.ticket.model.entity.TicketStatus;
import com.danzzan.domain.ticket.repository.UserTicketRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminEventServiceImplTest {

    @Mock
    private FestivalEventRepository festivalEventRepository;

    @Mock
    private UserTicketRepository userTicketRepository;

    private AdminEventServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminEventServiceImpl(festivalEventRepository, userTicketRepository);
    }

    @Test
    void getEventStats_권리포기티켓은_별도집계하고_잔여수용인원에서는_소진으로_계산한다() {
        FestivalEvent event = event(10L, 100);
        when(festivalEventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(userTicketRepository.countByEventIdAndStatus(10L, TicketStatus.CONFIRMED)).thenReturn(10L);
        when(userTicketRepository.countByEventIdAndStatus(10L, TicketStatus.ISSUED)).thenReturn(30L);
        when(userTicketRepository.countByEventIdAndStatus(10L, TicketStatus.CANCELLED_WITHDRAWAL)).thenReturn(5L);
        when(userTicketRepository.countByEventIdAndStatusIn(
                10L,
                List.of(TicketStatus.CONFIRMED, TicketStatus.ISSUED, TicketStatus.CANCELLED_WITHDRAWAL)
        )).thenReturn(45L);

        EventStatsResponseDTO response = service.getEventStats(10L);

        assertThat(response.getTotalTickets()).isEqualTo(45L);
        assertThat(response.getTicketsConfirmed()).isEqualTo(10L);
        assertThat(response.getTicketsIssued()).isEqualTo(30L);
        assertThat(response.getTicketsCancelledByWithdrawal()).isEqualTo(5L);
        assertThat(response.getRemainingCapacity()).isEqualTo(55);
        assertThat(response.getIssueRate()).isEqualTo(75.0);
    }

    @Test
    void listIncludesConfiguredEventsAndRetainsFestivalDayInsteadOfListIndex() {
        var event = event(10L, 100);
        event.rename("새 축제 DAY 3");
        when(festivalEventRepository.findConfiguredEvents()).thenReturn(List.of(event));
        var response = service.listEvents();
        assertThat(response.getEvents()).hasSize(1);
        assertThat(response.getEvents().get(0).getDayLabel()).isEqualTo("DAY 3");
        assertThat(response.getEvents().get(0).getTitle()).isEqualTo("새 축제 DAY 3");
    }

    private FestivalEvent event(Long id, int totalCapacity) {
        FestivalEvent event = FestivalEvent.builder()
                .title("dan")
                .eventDate(LocalDate.of(2026, 5, 13))
                .ticketingStartTime(LocalDateTime.of(2026, 5, 13, 18, 0))
                .ticketingStatus(TicketingStatus.OPEN)
                .totalCapacity(totalCapacity)
                .build();
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }
}
