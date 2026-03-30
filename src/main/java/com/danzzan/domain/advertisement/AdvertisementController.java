package com.danzzan.domain.advertisement;

import com.danzzan.domain.advertisement.dto.AdvertisementResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/ads")
@RequiredArgsConstructor
public class AdvertisementController {

    private final AdvertisementQueryService advertisementQueryService;

    /**
     * placement별 활성 광고 1건 조회.
     * 없으면 204 No Content.
     */
    @GetMapping
    public ResponseEntity<AdvertisementResponse> getAd(
            @RequestParam("placement") AdvertisementPlacement placement
    ) {
        Optional<AdvertisementResponse> opt = advertisementQueryService.getActiveAd(placement);
        return opt.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * 활성화된 모든 광고 목록 조회 (홈/내티켓 캐러셀용).
     */
    @GetMapping("/list")
    public ResponseEntity<List<AdvertisementResponse>> getAllActiveAds() {
        return ResponseEntity.ok(advertisementQueryService.getAllActiveAds());
    }
}
