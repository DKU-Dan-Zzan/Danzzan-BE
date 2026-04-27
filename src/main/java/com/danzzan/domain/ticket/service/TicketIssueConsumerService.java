package com.danzzan.domain.ticket.service;

import com.danzzan.domain.event.model.entity.FestivalEvent;
import com.danzzan.domain.event.repository.FestivalEventRepository;
import com.danzzan.domain.ticket.consumer.exception.NonRetryableTicketIssueException;
import com.danzzan.domain.ticket.kafka.TicketIssueRequestedEvent;
import com.danzzan.domain.ticket.model.entity.TicketIssueRequest;
import com.danzzan.domain.ticket.model.entity.TicketIssueRequestStatus;
import com.danzzan.domain.ticket.model.entity.UserTicket;
import com.danzzan.domain.ticket.repository.TicketIssueRequestRepository;
import com.danzzan.domain.ticket.repository.UserTicketRepository;
import com.danzzan.domain.user.model.entity.User;
import com.danzzan.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketIssueConsumerService {

    public enum ProcessingResult {
        ISSUED,
        ALREADY_SUCCESS,
        ALREADY_FAILED
    }

    private final TicketIssueRequestRepository ticketIssueRequestRepository;
    private final UserTicketRepository userTicketRepository;
    private final FestivalEventRepository festivalEventRepository;
    private final UserRepository userRepository;

    @Transactional
    public ProcessingResult processIssueRequested(TicketIssueRequestedEvent event) {
        TicketIssueRequest request = ticketIssueRequestRepository.findByRequestId(event.requestId())
                .orElseThrow(() -> new NonRetryableTicketIssueException("ticket_issue_requests not found requestId=" + event.requestId()));

        if (request.getStatus() == TicketIssueRequestStatus.SUCCESS) {
            return ProcessingResult.ALREADY_SUCCESS;
        }
        if (request.getStatus() == TicketIssueRequestStatus.FAILED) {
            return ProcessingResult.ALREADY_FAILED;
        }
        if (request.getStatus() != TicketIssueRequestStatus.PROCESSING) {
            throw new NonRetryableTicketIssueException("invalid request status: " + request.getStatus());
        }

        FestivalEvent festivalEvent = festivalEventRepository.findById(event.eventId())
                .orElseThrow(() -> new NonRetryableTicketIssueException("festival_event not found id=" + event.eventId()));
        User user = userRepository.findById(event.userId())
                .orElseThrow(() -> new NonRetryableTicketIssueException("user not found id=" + event.userId()));

        int order = (int) (festivalEvent.getTotalCapacity() - event.remaining());
        if (order <= 0) {
            throw new NonRetryableTicketIssueException("invalid ticket order calculated: " + order);
        }

        try {
            userTicketRepository.save(UserTicket.builder()
                    .user(user)
                    .event(festivalEvent)
                    .ticketingOrder(order)
                    .seq(event.seq())
                    .build());
        } catch (DataIntegrityViolationException e) {
            if (!userTicketRepository.existsByUserIdAndEventId(event.userId(), event.eventId())) {
                throw e;
            }
            log.info("user_ticket unique 충돌 -> 이미 발급된 요청으로 수렴 requestId={} eventId={} userId={}",
                    event.requestId(), event.eventId(), event.userId());
        }

        request.markSuccess(LocalDateTime.now());
        return ProcessingResult.ISSUED;
    }

    @Transactional
    public void markFailed(String requestId, String errorCode, String errorReason) {
        ticketIssueRequestRepository.findByRequestId(requestId).ifPresent(request -> {
            if (request.getStatus() == TicketIssueRequestStatus.SUCCESS) {
                return;
            }
            request.markFailed(errorCode, errorReason, LocalDateTime.now());
        });
    }
}
