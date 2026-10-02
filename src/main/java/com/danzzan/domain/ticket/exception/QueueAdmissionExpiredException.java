package com.danzzan.domain.ticket.exception;

/** 예매 오픈 시각과 구분되는 대기열 입장 권한 만료/미발급 오류. */
public class QueueAdmissionExpiredException extends RuntimeException {
    public QueueAdmissionExpiredException() {
        super("예매 가능 시간이 만료되었습니다. 대기열에 다시 참여해주세요.");
    }
}
