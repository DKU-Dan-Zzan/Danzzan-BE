package com.danzzan.domain.advertisement;

import com.danzzan.domain.advertisement.dto.AdvertisementResponse;
import com.danzzan.domain.advertisement.dto.CreateAdvertisementRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ads")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAdvertisementController {

    private final AdminAdvertisementService adminAdvertisementService;

    /**
     * 광고 생성 (같은 위치에 기존 광고가 있으면 교체)
     */
    @PostMapping
    public ResponseEntity<AdvertisementResponse> createOrReplace(
            @Valid @RequestBody CreateAdvertisementRequest request
    ) {
        return ResponseEntity.ok(adminAdvertisementService.createOrReplace(request));
    }
}
