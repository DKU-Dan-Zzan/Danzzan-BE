package com.danzzan.domain.ticket.service.support;

import com.danzzan.domain.ticket.redis.TicketRequestStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

@Component
public class ClaimOutcomeMetrics {

    private final Map<TicketRequestStatus, LongAdder> counters = new EnumMap<>(TicketRequestStatus.class);

    public ClaimOutcomeMetrics() {
        counters.put(TicketRequestStatus.SUCCESS, new LongAdder());
        counters.put(TicketRequestStatus.SOLD_OUT, new LongAdder());
        counters.put(TicketRequestStatus.ALREADY, new LongAdder());
    }

    public long increment(TicketRequestStatus status) {
        LongAdder counter = counters.get(status);
        if (counter == null) {
            return 0L;
        }
        counter.increment();
        return counter.sum();
    }
}
