package com.danzzan.domain.ticket.model.entity;

public enum TicketStatus {
    CONFIRMED("예매 완료"),   // 예매 성공 (미수령)
    ISSUED("팔찌 수령 완료"), // 팔찌 수령 완료
    CANCELLED_WITHDRAWAL("회원 탈퇴로 인한 권리 포기"); // 탈퇴로 인한 미사용 티켓 권리포기

    private final String name;

    TicketStatus(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
