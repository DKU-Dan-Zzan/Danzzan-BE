package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.redis.TicketRedisKeys;
import com.danzzan.domain.ticket.redis.TicketRequestStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class TicketStatusServiceImpl implements TicketStatusService {

    private static final Set<TicketRequestStatus> TERMINAL_STATUSES = Set.of(
            TicketRequestStatus.SUCCESS,
            TicketRequestStatus.SOLD_OUT,
            TicketRequestStatus.ALREADY
    );

    private final StringRedisTemplate redisTemplate;
    private final QueueService queueService;

    @Override
    public TicketRequestStatus getStatus(String eventId, String userId) {
        // 1. 터미널 상태 확인 (Lua Script가 저장한 최종 결과)
        String statusValue = redisTemplate.opsForValue().get(TicketRedisKeys.statusKey(eventId, userId));
        if (statusValue != null && !statusValue.isBlank()) {
            try {
                TicketRequestStatus status = TicketRequestStatus.valueOf(statusValue);
                if (TERMINAL_STATUSES.contains(status)) {
                    return status;
                }
            } catch (IllegalArgumentException ignored) {
                return TicketRequestStatus.NONE;
            }
        }

        // 2. gate 키 확인 → 스케줄러가 입장 허가한 상태
        if (Boolean.TRUE.equals(redisTemplate.hasKey(TicketRedisKeys.gateUserKey(eventId, userId)))) {
            return TicketRequestStatus.ADMITTED;
        }

        // 3. 대기열 순번 확인 → 대기 중
        if (queueService.getQueuePosition(eventId, userId) != null) {
            return TicketRequestStatus.WAITING;
        }

        return TicketRequestStatus.NONE;
    }

    @Override
    public Long getQueuePosition(String eventId, String userId) {
        return queueService.getQueuePosition(eventId, userId);
    }
}
