package com.danzzan.domain.advertisement.controller;

import com.danzzan.domain.advertisement.dto.response.AdvertisementResponse;
import com.danzzan.domain.advertisement.model.entity.AdvertisementPlacement;
import com.danzzan.domain.advertisement.service.AdvertisementQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 클라이언트(앱)용 광고 조회 API.
 * GET /api/ads?placement=HOME
 * - 해당 placement에 등록된 활성 광고가 있으면 반환
 * - 없으면 GLOBAL 광고 중 랜덤 1건 반환
 */
@RestController
@RequestMapping("/api/ads")
@RequiredArgsConstructor
public class AdvertisementController {

    private final AdvertisementQueryService advertisementQueryService;

    @GetMapping
    public ResponseEntity<List<AdvertisementResponse>> getAds(
            @RequestParam(name = "placement") AdvertisementPlacement placement) {
        return ResponseEntity.ok(advertisementQueryService.getAdsByPlacement(placement));
    }
}
