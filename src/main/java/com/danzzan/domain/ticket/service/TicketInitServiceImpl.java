package com.danzzan.domain.ticket.service;

import com.danzzan.domain.event.model.entity.TicketingStatus;
import com.danzzan.domain.ticket.dto.AdminTicketInitResponseDTO;
import com.danzzan.domain.ticket.redis.TicketRedisKeys;
import com.danzzan.domain.ticket.service.support.ClaimLuaProtocol;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TicketInitServiceImpl implements TicketInitService {

    private static final String STOCK_SUFFIX = ":stock";
    private static final long SCAN_COUNT = 500L;
    private static final int UNLINK_BATCH_SIZE = 500;

    private final StringRedisTemplate redisTemplate;

    @Override
    public AdminTicketInitResponseDTO initStock(String eventId, Long stock) {
        clearEventKeys(eventId);
        redisTemplate.opsForValue().set(TicketRedisKeys.stockKey(eventId), String.valueOf(stock));

        return AdminTicketInitResponseDTO.builder()
                .eventId(eventId)
                .stock(stock)
                .build();
    }

    @Override
    public void purgeEvent(String eventId) {
        clearEventKeys(eventId);
        redisTemplate.delete(TicketRedisKeys.stockKey(eventId));
        redisTemplate.delete(TicketRedisKeys.eventStatusKey(eventId));
    }

    /** 재고 키를 뺀 나머지 이벤트 키를 모두 지운다. */
    private void clearEventKeys(String eventId) {
        String eventPrefix = eventPrefixFromStockKey(TicketRedisKeys.stockKey(eventId));

        // Lua claim 관련 키
        unlinkByPattern(eventPrefix + ":user:*");
        unlinkByPattern(eventPrefix + ":status:*");

        // 대기열 상태 Hash 키
        unlinkByPattern(eventPrefix + ":quser:*");

        // 중복 방지 키
        unlinkByPattern(eventPrefix + ":dedup:*");

        // 대기열/READY/ACTIVE 키
        redisTemplate.delete(TicketRedisKeys.queueKey(eventId));
        redisTemplate.delete(TicketRedisKeys.seqKey(eventId));
        redisTemplate.delete(TicketRedisKeys.readyKey(eventId));
        redisTemplate.delete(TicketRedisKeys.activeKey(eventId));
        redisTemplate.delete(TicketRedisKeys.closedCleanupKey(eventId));
    }

    private String eventPrefixFromStockKey(String stockKey) {
        if (!stockKey.endsWith(STOCK_SUFFIX)) {
            throw new IllegalStateException("stock key must end with :stock");
        }
        return stockKey.substring(0, stockKey.length() - STOCK_SUFFIX.length());
    }

    private void unlinkByPattern(String pattern) {
        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(SCAN_COUNT)
                .build();

        List<String> batch = new ArrayList<>(UNLINK_BATCH_SIZE);
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() >= UNLINK_BATCH_SIZE) {
                    unlinkBatch(batch);
                }
            }
            unlinkBatch(batch);
        }
    }

    @Override
    public boolean restoreStockIfMissing(String eventId, long stock) {
        String stockKey = TicketRedisKeys.stockKey(eventId);
        Boolean set = redisTemplate.opsForValue().setIfAbsent(stockKey, String.valueOf(stock));
        return Boolean.TRUE.equals(set);
    }

    @Override
    public long syncIssuedUsers(String eventId, List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return 0L;
        }

        Map<String, String> userKeys = new LinkedHashMap<>(userIds.size());
        for (Long userId : userIds) {
            if (userId == null) {
                continue;
            }
            userKeys.put(
                    TicketRedisKeys.userKey(eventId, String.valueOf(userId)),
                    ClaimLuaProtocol.USER_CLAIMED_VALUE
            );
        }

        if (userKeys.isEmpty()) {
            return 0L;
        }
        redisTemplate.opsForValue().multiSet(userKeys);
        return userKeys.size();
    }

    @Override
    public void setEventStatus(String eventId, TicketingStatus status) {
        if (status == null) {
            return;
        }
        redisTemplate.opsForValue().set(TicketRedisKeys.eventStatusKey(eventId), status.name());
    }

    private void unlinkBatch(List<String> batch) {
        if (batch.isEmpty()) return;
        redisTemplate.unlink(batch);
        batch.clear();
    }
}
