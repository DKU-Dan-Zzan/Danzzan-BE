package com.danzzan.domain.ticket.service;

import com.danzzan.domain.ticket.redis.TicketRedisKeys;
import com.danzzan.domain.ticket.redis.TicketRequestStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdmissionServiceImpl implements AdmissionService {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public TicketRequestStatus admit(String eventId, String userId) {
        String gateUserKey = TicketRedisKeys.gateUserKey(eventId, userId);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(gateUserKey))) {
            return TicketRequestStatus.ADMITTED;
        }
        return TicketRequestStatus.WAITING;
    }
}
