package com.danzzan.domain.ticket.exception;

public class ReserveProcessingException extends RuntimeException {

    public ReserveProcessingException() {
        super("예매 요청 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
    }

    public ReserveProcessingException(String message) {
        super(message);
    }
}
