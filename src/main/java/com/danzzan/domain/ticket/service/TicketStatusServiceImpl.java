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

    private static final Set<TicketRequestStatus> TERMINAL_STATUSES =
            Set.of(TicketRequestStatus.SUCCESS, TicketRequestStatus.SOLD_OUT, TicketRequestStatus.ALREADY);

    private final StringRedisTemplate redisTemplate;
    private final QueueService queueService;

    @Override
    public TicketRequestStatus getStatus(String eventId, String userId) {
        // 1. Lua가 기록한 최종 상태 확인 (SUCCESS / SOLD_OUT / ALREADY)
        String statusKey = TicketRedisKeys.statusKey(eventId, userId);
        String statusValue = redisTemplate.opsForValue().get(statusKey);
        if (statusValue != null && !statusValue.isBlank()) {
            try {
                TicketRequestStatus s = TicketRequestStatus.valueOf(statusValue);
                if (TERMINAL_STATUSES.contains(s)) {
                    return s;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        // 2. 스케줄러가 발급한 gate 키 확인 → ADMITTED
        String gateUserKey = TicketRedisKeys.gateUserKey(eventId, userId);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(gateUserKey))) {
            return TicketRequestStatus.ADMITTED;
        }

        // 3. 대기열 존재 여부 → WAITING
        Long position = queueService.getQueuePosition(eventId, userId);
        if (position != null) {
            return TicketRequestStatus.WAITING;
        }

        return TicketRequestStatus.NONE;
    }
}
