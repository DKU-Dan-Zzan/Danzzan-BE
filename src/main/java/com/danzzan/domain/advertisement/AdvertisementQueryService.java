package com.danzzan.domain.advertisement;

import com.danzzan.domain.advertisement.dto.AdvertisementResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdvertisementQueryService {

    private final AdvertisementRepository advertisementRepository;

    /**
     * placement별 활성 광고를 1개만 반환합니다.
     * 없으면 Optional.empty()를 반환합니다.
     */
    @Transactional(readOnly = true)
    public Optional<AdvertisementResponse> getActiveAd(AdvertisementPlacement placement) {
        return advertisementRepository
                .findFirstByPlacementAndIsActiveTrueAndDeletedAtIsNullOrderByCreatedAtDesc(placement)
                .map(AdvertisementResponse::from);
    }

    /**
     * 삭제되지 않은 모든 광고를 반환합니다 (공개 캐러셀 API용).
     * isActive 여부와 무관하게 soft-delete되지 않은 광고 전체를 반환합니다.
     */
    @Transactional(readOnly = true)
    public List<AdvertisementResponse> getAllActiveAds() {
        return advertisementRepository
                .findAllByDeletedAtIsNullOrderByCreatedAtDesc()
                .stream()
                .map(AdvertisementResponse::from)
                .toList();
    }
}
