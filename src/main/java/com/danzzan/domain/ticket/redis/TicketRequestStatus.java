package com.danzzan.domain.ticket.redis;

public enum TicketRequestStatus {
    NONE("미참여"),
    WAITING("대기중"),
    ADMITTED("입장 가능"),
    PROCESSING("처리중"),
    SUCCESS("예매 완료"),
    FAILED("처리 실패"),
    SOLD_OUT("매진"),
    ALREADY("이미 예매함");

    private final String name;

    TicketRequestStatus(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
