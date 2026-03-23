package com.danzzan.domain.advertisement;

import com.danzzan.domain.advertisement.dto.AdvertisementResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

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
}
