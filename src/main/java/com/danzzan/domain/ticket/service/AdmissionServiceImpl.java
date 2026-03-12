package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.redis.TicketRedisKeys;
import com.danzzan.domain.ticket.redis.TicketRequestStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdmissionServiceImpl implements AdmissionService {

    private final StringRedisTemplate redisTemplate;

    /**
     * 스케줄러가 발급한 gate 키가 있으면 ADMITTED, 없으면 WAITING 반환.
     */
    @Override
    public TicketRequestStatus admit(String eventId, String userId) {
        String gateKey = TicketRedisKeys.gateUserKey(eventId, userId);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(gateKey))) {
            return TicketRequestStatus.ADMITTED;
        }
        return TicketRequestStatus.WAITING;
    }
}
