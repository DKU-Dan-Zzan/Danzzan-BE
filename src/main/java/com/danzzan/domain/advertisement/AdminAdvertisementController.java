package com.danzzan.domain.advertisement;

import com.danzzan.domain.advertisement.dto.AdvertisementResponse;
import com.danzzan.domain.advertisement.dto.CreateAdvertisementRequest;
import com.danzzan.domain.advertisement.dto.request.PresignAdvertisementImageRequest;
import com.danzzan.domain.advertisement.dto.request.SetAdvertisementActiveRequest;
import com.danzzan.domain.advertisement.dto.response.AdvertisementImagePresignResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ads")
@RequiredArgsConstructor
@PreAuthorize("@userAdminAuthorizationService.hasAdminRole(authentication)")
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

    /**
     * 광고 이미지 업로드용 S3 Presigned PUT URL 발급.
     */
    @PostMapping("/images/presign")
    public ResponseEntity<AdvertisementImagePresignResponse> presignAdImage(
            @Valid @RequestBody PresignAdvertisementImageRequest request
    ) {
        return ResponseEntity.ok(adminAdvertisementService.presignAdImage(request));
    }

    /**
     * 레거시 프론트 호환을 위한 광고 업로드 URL 발급 엔드포인트.
     */
    @PostMapping("/upload-url")
    public ResponseEntity<AdvertisementImagePresignResponse> uploadUrl(
            @Valid @RequestBody PresignAdvertisementImageRequest request
    ) {
        return ResponseEntity.ok(adminAdvertisementService.presignAdImage(request));
    }

    /**
     * placement별 광고 노출 on/off. isActive=true면 해당 슬롯의 최신(미삭제) 광고만 활성화합니다.
     */
    @PatchMapping("/{placement}/active")
    public ResponseEntity<?> setPlacementActive(
            @PathVariable AdvertisementPlacement placement,
            @Valid @RequestBody SetAdvertisementActiveRequest request
    ) {
        if (Boolean.TRUE.equals(request.getIsActive())) {
            return ResponseEntity.ok(adminAdvertisementService.activatePlacement(placement));
        }
        adminAdvertisementService.deactivatePlacement(placement);
        return ResponseEntity.noContent().build();
    }

    /**
     * 현재 노출 중인 광고 소프트 삭제(DB 행 유지, 공개 API에서는 미노출).
     */
    @DeleteMapping("/{placement}")
    public ResponseEntity<Void> softDeleteByPlacement(@PathVariable AdvertisementPlacement placement) {
        adminAdvertisementService.softDeleteByPlacement(placement);
        return ResponseEntity.noContent().build();
    }

    /**
     * ID로 특정 광고를 소프트 삭제합니다.
     */
    @DeleteMapping("/item/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable Long id) {
        adminAdvertisementService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
