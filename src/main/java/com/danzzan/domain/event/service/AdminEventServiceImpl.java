package com.danzzan.domain.event.service;

import com.danzzan.domain.event.dto.EventListResponseDTO;
import com.danzzan.domain.event.dto.EventStatsResponseDTO;
import com.danzzan.domain.event.dto.EventSummaryDTO;
import com.danzzan.domain.event.exception.EventNotFoundException;
import com.danzzan.domain.event.model.entity.FestivalEvent;
import com.danzzan.domain.event.repository.FestivalEventRepository;
import com.danzzan.domain.ticket.model.entity.TicketStatus;
import com.danzzan.domain.ticket.repository.UserTicketRepository;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminEventServiceImpl implements AdminEventService {

    private static final List<TicketStatus> CONSUMED_TICKET_STATUSES = List.of(
            TicketStatus.CONFIRMED,
            TicketStatus.ISSUED,
            TicketStatus.CANCELLED_WITHDRAWAL
    );

    private final FestivalEventRepository festivalEventRepository;
    private final UserTicketRepository userTicketRepository;

    @Override
    public EventListResponseDTO listEvents() {
        List<FestivalEvent> events = festivalEventRepository.findAll(
                Sort.by(Sort.Direction.ASC, "eventDate")
        );

        Map<LocalDate, Integer> dayIndex = buildDayIndex(events);

        List<EventSummaryDTO> summaries = events.stream()
                .map(event -> EventSummaryDTO.builder()
                        .eventId(event.getId())
                        .title(event.getTitle())
                        .dayLabel("DAY " + dayIndex.get(event.getEventDate()))
                        .eventDate(event.getEventDate().toString())
                        .ticketingStatus(event.getTicketingStatus())
                        .totalCapacity(event.getTotalCapacity())
                        .build())
                .toList();

        return EventListResponseDTO.builder()
                .events(summaries)
                .build();
    }

    @Override
    public EventStatsResponseDTO getEventStats(Long eventId) {
        FestivalEvent event = festivalEventRepository.findById(eventId)
                .orElseThrow(EventNotFoundException::new);

        long totalTickets = userTicketRepository.countByEventIdAndStatusIn(eventId, CONSUMED_TICKET_STATUSES);
        long ticketsConfirmed = userTicketRepository.countByEventIdAndStatus(eventId, TicketStatus.CONFIRMED);
        long ticketsIssued = userTicketRepository.countByEventIdAndStatus(eventId, TicketStatus.ISSUED);
        long ticketsCancelledByWithdrawal =
                userTicketRepository.countByEventIdAndStatus(eventId, TicketStatus.CANCELLED_WITHDRAWAL);

        int totalCapacity = event.getTotalCapacity();
        int remainingCapacity = Math.max(0, totalCapacity - (int) totalTickets);
        long activeTickets = ticketsConfirmed + ticketsIssued;
        double issueRate = activeTickets == 0 ? 0.0 : ((double) ticketsIssued / activeTickets) * 100.0;

        return EventStatsResponseDTO.builder()
                .eventId(event.getId())
                .title(event.getTitle())
                .eventDate(event.getEventDate().toString())
                .totalCapacity(totalCapacity)
                .totalTickets(totalTickets)
                .ticketsConfirmed(ticketsConfirmed)
                .ticketsIssued(ticketsIssued)
                .ticketsCancelledByWithdrawal(ticketsCancelledByWithdrawal)
                .issueRate(issueRate)
                .remainingCapacity(remainingCapacity)
                .build();
    }

    private Map<LocalDate, Integer> buildDayIndex(List<FestivalEvent> events) {
        Map<LocalDate, Integer> indexByDate = new LinkedHashMap<>();
        int fallbackIdx = 1;
        for (FestivalEvent event : events) {
            LocalDate date = event.getEventDate();
            if (!indexByDate.containsKey(date)) {
                Integer dayFromTitle = extractDayNumber(event.getTitle());
                indexByDate.put(date, dayFromTitle != null ? dayFromTitle : fallbackIdx);
                fallbackIdx++;
            }
        }
        return indexByDate;
    }

    private Integer extractDayNumber(String title) {
        if (title == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("DAY\\s*(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(title);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }
}
