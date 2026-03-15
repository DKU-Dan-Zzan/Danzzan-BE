package com.danzzan.domain.advertisement.service;

import com.danzzan.domain.advertisement.dto.response.AdvertisementResponse;
import com.danzzan.domain.advertisement.model.entity.Advertisement;
import com.danzzan.domain.advertisement.model.entity.AdvertisementPlacement;
import com.danzzan.domain.advertisement.repository.AdvertisementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 클라이언트용 광고 조회 서비스.
 * 1) 해당 placement에 등록된 활성 광고가 있으면 반환 (priority 순)
 * 2) 없으면 GLOBAL 광고 중 랜덤으로 반환
 */
@Service
@RequiredArgsConstructor
public class AdvertisementQueryService {

    private final AdvertisementRepository advertisementRepository;

    @Transactional(readOnly = true)
    public List<AdvertisementResponse> getAdsByPlacement(AdvertisementPlacement placement) {
        LocalDate today = LocalDate.now();

        List<Advertisement> placementAds = advertisementRepository.findActiveByPlacementAndDate(placement, today);
        if (!placementAds.isEmpty()) {
            return placementAds.stream().map(AdvertisementResponse::from).collect(Collectors.toList());
        }

        List<Advertisement> globalAds = advertisementRepository.findActiveGlobalByDate(today);
        if (globalAds.isEmpty()) {
            return Collections.emptyList();
        }

        Advertisement random = globalAds.get(ThreadLocalRandom.current().nextInt(globalAds.size()));
        return List.of(AdvertisementResponse.from(random));
    }
}
