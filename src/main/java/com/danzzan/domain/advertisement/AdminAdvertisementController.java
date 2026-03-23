package com.danzzan.domain.advertisement;

import com.danzzan.domain.advertisement.dto.AdvertisementImagePresignResponse;
import com.danzzan.domain.advertisement.dto.AdvertisementResponse;
import com.danzzan.domain.advertisement.dto.CreateAdvertisementRequest;
import com.danzzan.domain.advertisement.dto.PresignAdvertisementImageRequest;
import com.danzzan.infra.s3.S3PresignService;
import com.danzzan.infra.s3.S3PresignedPutResult;
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
@PreAuthorize("@userAdminAuthorizationService.hasAdminRole(authentication)")
public class AdminAdvertisementController {

    private final AdminAdvertisementService adminAdvertisementService;
    private final S3PresignService s3PresignService;

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
     * 광고 이미지 Presigned PUT URL 발급.
     */
    @PostMapping("/images/presign")
    public ResponseEntity<AdvertisementImagePresignResponse> presignAdImage(
            @Valid @RequestBody PresignAdvertisementImageRequest request
    ) {
        return ResponseEntity.ok(buildPresignResponse(request));
    }

    /**
     * 레거시 프론트 호환을 위한 광고 업로드 URL 발급 엔드포인트.
     */
    @PostMapping("/upload-url")
    public ResponseEntity<AdvertisementImagePresignResponse> uploadUrl(
            @Valid @RequestBody PresignAdvertisementImageRequest request
    ) {
        return ResponseEntity.ok(buildPresignResponse(request));
    }

    private AdvertisementImagePresignResponse buildPresignResponse(PresignAdvertisementImageRequest request) {
        S3PresignedPutResult result = s3PresignService.presignPutAdImage(
                request.getFileName(),
                request.getContentType(),
                request.getFileSize()
        );
        return AdvertisementImagePresignResponse.from(result);
    }
}
