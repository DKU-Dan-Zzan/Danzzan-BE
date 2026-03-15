package com.danzzan.domain.advertisement.controller;

import com.danzzan.domain.advertisement.dto.request.AdImagePresignRequest;
import com.danzzan.domain.advertisement.dto.request.CreateAdvertisementRequest;
import com.danzzan.domain.advertisement.dto.request.SetActiveRequest;
import com.danzzan.domain.advertisement.dto.request.UpdateAdvertisementRequest;
import com.danzzan.domain.advertisement.dto.response.AdImagePresignResponse;
import com.danzzan.domain.advertisement.dto.response.AdvertisementResponse;
import com.danzzan.domain.advertisement.service.AdminAdvertisementService;
import com.danzzan.infra.s3.S3PresignService;
import com.danzzan.infra.s3.S3PresignedPutResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/ads")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAdvertisementController {

    private final AdminAdvertisementService adminAdvertisementService;
    private final S3PresignService s3PresignService;

    /** 광고 생성 */
    @PostMapping
    public ResponseEntity<AdvertisementResponse> create(@Valid @RequestBody CreateAdvertisementRequest request) {
        return ResponseEntity.ok(adminAdvertisementService.create(request));
    }

    /** 광고 목록 조회 (페이지네이션) */
    @GetMapping
    public ResponseEntity<Page<AdvertisementResponse>> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(adminAdvertisementService.findAll(pageable));
    }

    /** 광고 단건 조회 */
    @GetMapping("/{id}")
    public ResponseEntity<AdvertisementResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(adminAdvertisementService.findById(id));
    }

    /** 광고 수정 */
    @PutMapping("/{id}")
    public ResponseEntity<AdvertisementResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAdvertisementRequest request) {
        return ResponseEntity.ok(adminAdvertisementService.update(id, request));
    }

    /** 광고 삭제 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        adminAdvertisementService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** 광고 활성화 / 비활성화 */
    @PatchMapping("/{id}/active")
    public ResponseEntity<AdvertisementResponse> setActive(
            @PathVariable Long id,
            @Valid @RequestBody SetActiveRequest request) {
        return ResponseEntity.ok(adminAdvertisementService.setActive(id, request));
    }

    /**
     * 광고 이미지 업로드용 Presigned URL 발급
     * POST /api/admin/ads/upload-url
     */
    @PostMapping("/upload-url")
    public ResponseEntity<AdImagePresignResponse> createUploadUrl(
            @Valid @RequestBody AdImagePresignRequest request
    ) {
        S3PresignedPutResult result = s3PresignService.presignPutAdImage(
                request.getFileName(),
                request.getContentType(),
                request.getFileSize()
        );
        return ResponseEntity.ok(AdImagePresignResponse.from(result));
    }
}
