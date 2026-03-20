package com.danzzan.domain.event.service;

public interface EventOpenService {

    /**
     * 이벤트를 OPEN으로 전환하고 Redis stock을 초기화합니다.
     * READY 상태인 경우에만 전환되며, 이미 OPEN/CLOSED이면 false를 반환합니다.
     * 수동/자동 경로 모두 이 메서드를 사용합니다.
     *
     * @return true = 이번에 OPEN 전환 성공, false = 이미 OPEN이거나 CLOSED (skip)
     */
    boolean openNow(Long eventId);
}
