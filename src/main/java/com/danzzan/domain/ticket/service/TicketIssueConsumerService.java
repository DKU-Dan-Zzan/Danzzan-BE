package com.danzzan.domain.ticket.service;

import com.danzzan.domain.event.model.entity.FestivalEvent;
import com.danzzan.domain.event.repository.FestivalEventRepository;
import com.danzzan.domain.ticket.consumer.exception.NonRetryableTicketIssueException;
import com.danzzan.domain.ticket.kafka.TicketIssueRequestedEvent;
import com.danzzan.domain.ticket.model.entity.TicketIssueRequest;
import com.danzzan.domain.ticket.model.entity.TicketIssueRequestStatus;
import com.danzzan.domain.ticket.model.entity.TicketStatus;
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
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketIssueConsumerService {

    private static final String USER_WITHDRAWN_ERROR_CODE = "USER_WITHDRAWN";
    private static final List<TicketStatus> CONSUMED_TICKET_STATUSES = List.of(
            TicketStatus.CONFIRMED,
            TicketStatus.ISSUED,
            TicketStatus.CANCELLED_WITHDRAWAL
    );

    public enum ProcessingResult {
        ISSUED,
        WITHDRAWN_CANCELLED,
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
        User user = userRepository.findByIdForUpdate(event.userId())
                .orElseThrow(() -> new NonRetryableTicketIssueException("user not found id=" + event.userId()));

        if (user.isDeleted()) {
            createWithdrawalTicketIfAbsent(event, request, festivalEvent, user);
            request.markFailed(USER_WITHDRAWN_ERROR_CODE, "회원 탈퇴로 티켓 권리포기 처리", LocalDateTime.now());
            return ProcessingResult.WITHDRAWN_CANCELLED;
        }

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

    private void createWithdrawalTicketIfAbsent(
            TicketIssueRequestedEvent event,
            TicketIssueRequest request,
            FestivalEvent festivalEvent,
            User user
    ) {
        if (userTicketRepository.existsByUserIdAndEventIdAndStatusIn(
                event.userId(), event.eventId(), CONSUMED_TICKET_STATUSES)) {
            return;
        }

        Long remaining = request.getRemainingAfterClaim() != null ? request.getRemainingAfterClaim() : event.remaining();
        int order = (int) (festivalEvent.getTotalCapacity() - remaining);
        if (order <= 0) {
            throw new NonRetryableTicketIssueException("invalid withdrawal ticket order calculated: " + order);
        }

        try {
            userTicketRepository.save(UserTicket.cancelledByWithdrawal(
                    user,
                    festivalEvent,
                    order,
                    request.getSeq() != null ? request.getSeq() : event.seq(),
                    LocalDateTime.now()
            ));
        } catch (DataIntegrityViolationException e) {
            if (!userTicketRepository.existsByUserIdAndEventIdAndStatusIn(
                    event.userId(), event.eventId(), CONSUMED_TICKET_STATUSES)) {
                throw e;
            }
            log.info("withdrawal ticket unique 충돌 -> 권리포기 처리로 수렴 requestId={} eventId={} userId={}",
                    event.requestId(), event.eventId(), event.userId());
        }
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
