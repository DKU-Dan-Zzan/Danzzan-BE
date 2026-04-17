package com.danzzan.domain.ticket.consumer.exception;

public class NonRetryableTicketIssueException extends RuntimeException {

    public NonRetryableTicketIssueException(String message) {
        super(message);
    }
}
