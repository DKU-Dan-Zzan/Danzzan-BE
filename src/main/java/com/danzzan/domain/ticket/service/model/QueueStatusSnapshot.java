package com.danzzan.domain.ticket.service.model;

/**
 * Redis Pipeline으로 단일 RTT에 수집한 유저 대기열 상태 스냅샷.
 *
 * @param claimStatus  claim 결과 문자열 (SUCCESS/SOLD_OUT/ALREADY, 없으면 null)
 * @param state        quser Hash의 state 필드
 * @param seq          quser Hash의 seq 필드
 * @param readyUntil   quser Hash의 readyUntil 필드 (READY 상태일 때만 유효)
 * @param queueRank    대기열 내 0-based 순위 (WAITING일 때만 유효, null이면 대기열 미포함)
 * @param stock        남은 재고 (null이면 stock 키 미존재)
 */
public record QueueStatusSnapshot(
        String claimStatus,
        String state,
        String seq,
        String readyUntil,
        Long queueRank,
        Long stock
) {}
