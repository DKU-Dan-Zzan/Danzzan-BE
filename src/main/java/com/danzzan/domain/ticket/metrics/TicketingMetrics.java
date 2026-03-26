package com.danzzan.domain.ticket.metrics;

import com.danzzan.domain.ticket.redis.TicketRequestStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 티케팅 도메인 전용 Micrometer 메트릭 컴포넌트.
 *
 * - ticket_queue_enter_total{result}  : 대기열 진입 결과별 카운터
 * - ticket_claim_total{result}        : 예매 시도(claim) 결과별 카운터
 * - ticket_queue_depth{event_id}      : 이벤트별 실시간 대기열 깊이 (Gauge)
 * - ticket_stock_remaining{event_id}  : 이벤트별 실시간 잔여 재고 (Gauge)
 * - ticket_admission_total{event_id}  : 이벤트별 누적 ADMITTED 처리 수 (Gauge)
 */
@Component
@Slf4j
public class TicketingMetrics {

    private final MeterRegistry registry;

    // 대기열 진입 카운터
    private final Counter enterWaiting;
    private final Counter enterAdmitted;
    private final Counter enterSoldOut;
    private final Counter enterAlready;

    // 예매(claim) 결과 카운터
    private final Counter claimSuccess;
    private final Counter claimSoldOut;
    private final Counter claimAlready;

    // 이벤트별 Gauge 값 (동적으로 등록)
    private final ConcurrentHashMap<String, AtomicLong> queueDepths = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> stockLevels = new ConcurrentHashMap<>();

    // 이벤트별 Counter (admission은 감소하지 않으므로 Counter가 의미론적으로 정확)
    private final ConcurrentHashMap<String, Counter> admissionCounters = new ConcurrentHashMap<>();

    public TicketingMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.enterWaiting  = counter("ticket_queue_enter_total", "result", "WAITING");
        this.enterAdmitted = counter("ticket_queue_enter_total", "result", "ADMITTED");
        this.enterSoldOut  = counter("ticket_queue_enter_total", "result", "SOLD_OUT");
        this.enterAlready  = counter("ticket_queue_enter_total", "result", "ALREADY");

        this.claimSuccess  = counter("ticket_claim_total", "result", "SUCCESS");
        this.claimSoldOut  = counter("ticket_claim_total", "result", "SOLD_OUT");
        this.claimAlready  = counter("ticket_claim_total", "result", "ALREADY");
    }

    // ── 대기열 진입 ──────────────────────────────────────────

    public void recordQueueEnter(TicketRequestStatus status) {
        switch (status) {
            case WAITING  -> enterWaiting.increment();
            case ADMITTED -> enterAdmitted.increment();
            case SOLD_OUT -> enterSoldOut.increment();
            case ALREADY  -> enterAlready.increment();
            default -> {}
        }
    }

    // ── 예매(claim) 결과 ──────────────────────────────────────

    public void recordClaim(TicketRequestStatus status) {
        switch (status) {
            case SUCCESS  -> claimSuccess.increment();
            case SOLD_OUT -> claimSoldOut.increment();
            case ALREADY  -> claimAlready.increment();
            default -> {}
        }
    }

    // ── 이벤트별 Gauge 업데이트 (스케줄러에서 호출) ────────────

    public void updateQueueDepth(String eventId, long depth) {
        queueDepths.computeIfAbsent(eventId, id -> {
            AtomicLong ref = new AtomicLong(0);
            Gauge.builder("ticket_queue_depth", ref, AtomicLong::get)
                    .tag("event_id", id)
                    .description("실시간 대기열 깊이 (WAITING 유저 수)")
                    .register(registry);
            return ref;
        }).set(depth);
    }

    public void updateStock(String eventId, long stock) {
        stockLevels.computeIfAbsent(eventId, id -> {
            AtomicLong ref = new AtomicLong(0);
            Gauge.builder("ticket_stock_remaining", ref, AtomicLong::get)
                    .tag("event_id", id)
                    .description("이벤트별 잔여 재고")
                    .register(registry);
            return ref;
        }).set(stock);
    }

    public void incrementAdmission(String eventId) {
        admissionCounters.computeIfAbsent(eventId, id ->
                Counter.builder("ticket_admission_total")
                        .tag("event_id", id)
                        .description("누적 ADMITTED 처리 수")
                        .register(registry)
        ).increment();
    }

    // ── 내부 헬퍼 ──────────────────────────────────────────────

    private Counter counter(String name, String tagKey, String tagValue) {
        return Counter.builder(name)
                .tag(tagKey, tagValue)
                .register(registry);
    }
}
