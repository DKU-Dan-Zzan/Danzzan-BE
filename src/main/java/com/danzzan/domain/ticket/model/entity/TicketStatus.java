package com.danzzan.domain.ticket.model.entity;

public enum TicketStatus {
    CONFIRMED("예매 완료"),   // 예매 성공 (미수령)
    ISSUED("팔찌 수령 완료"); // 팔찌 수령 완료

    private final String name;

    TicketStatus(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
