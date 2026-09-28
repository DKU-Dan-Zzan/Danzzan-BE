package com.danzzan.domain.festival.service;

import com.danzzan.domain.festival.entity.FestivalSetting;
import com.danzzan.domain.festival.repository.FestivalSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 티켓팅 API 를 열어 둘지 판단한다.
 *
 * 평소 조작은 관리자 축제 설정(festival_setting.ticketing_enabled)으로 한다.
 * app.ticketing.api-enabled 는 그 위에 있는 비상 차단 스위치다. 설정을 켜 두었더라도
 * 이 값을 false 로 배포하면 티켓팅이 닫힌다.
 *
 * 티켓팅은 순간 트래픽이 몰리는 경로라 요청마다 DB 를 보지 않고 짧게 캐시한다.
 * 관리자가 설정을 바꾸면 최대 CACHE_TTL 만큼 뒤에 반영된다.
 */
@Component
@RequiredArgsConstructor
public class TicketingAccessPolicy {

    private static final Duration CACHE_TTL = Duration.ofSeconds(5);

    private final FestivalSettingRepository festivalSettingRepository;

    @Value("${app.ticketing.api-enabled:true}")
    private boolean apiEnabledSwitch;

    private final AtomicReference<CachedValue> cache = new AtomicReference<>(null);

    @Transactional(readOnly = true)
    public boolean isTicketingEnabled() {
        if (!apiEnabledSwitch) {
            return false;
        }

        CachedValue cached = cache.get();
        Instant now = Instant.now();
        if (cached != null && cached.expiresAt.isAfter(now)) {
            return cached.enabled;
        }

        boolean enabled = festivalSettingRepository.findById(FestivalSetting.SINGLETON_ID)
                .map(FestivalSetting::isTicketingEnabled)
                .orElse(false);
        cache.set(new CachedValue(enabled, now.plus(CACHE_TTL)));
        return enabled;
    }

    /** 설정을 저장한 직후처럼 즉시 반영해야 할 때 쓴다. */
    public void invalidate() {
        cache.set(null);
    }

    private record CachedValue(boolean enabled, Instant expiresAt) {
    }
}
