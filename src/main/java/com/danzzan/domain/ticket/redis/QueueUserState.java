package com.danzzan.domain.ticket.redis;

public enum QueueUserState {
    WAITING,   // 대기열에 있음
    READY,     // 입장권 발급됨 (TTL 내 /reserve 가능)
    ACTIVE,    // /reserve 진행 중
    DONE,      // 예매 완료
    EXPIRED,   // READY TTL 초과
    CANCELLED  // 이탈/취소
}
