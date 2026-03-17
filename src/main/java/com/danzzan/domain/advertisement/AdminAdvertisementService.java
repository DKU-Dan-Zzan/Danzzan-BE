package com.danzzan.domain.advertisement;

import com.danzzan.domain.advertisement.dto.AdvertisementResponse;
import com.danzzan.domain.advertisement.dto.CreateAdvertisementRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminAdvertisementService {

    private final AdvertisementRepository advertisementRepository;

    /**
     * 새 광고를 생성하면서, 같은 placement의 기존 활성 광고는 모두 비활성화합니다.
     */
    @Transactional
    public AdvertisementResponse createOrReplace(CreateAdvertisementRequest request) {
        AdvertisementPlacement placement = request.getPlacement();

        // 기존 활성 광고 비활성화
        List<Advertisement> existing = advertisementRepository.findByPlacementOrderByCreatedAtDesc(placement);
        for (Advertisement ad : existing) {
            if (Boolean.TRUE.equals(ad.getIsActive())) {
                ad.setIsActive(false);
            }
        }

        Advertisement ad = new Advertisement();
        ad.setTitle(request.getTitle());
        ad.setImageUrl(request.getImageUrl());
        ad.setPlacement(placement);
        ad.setIsActive(true);

        return AdvertisementResponse.from(advertisementRepository.save(ad));
    }
}
