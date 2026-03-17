package com.danzzan.domain.notice.controller;

import com.danzzan.domain.notice.dto.request.CreateNoticeRequest;
import com.danzzan.domain.notice.dto.request.PresignNoticeImageRequest;
import com.danzzan.domain.notice.dto.request.UpdateEmergencyRequest;
import com.danzzan.domain.notice.dto.request.UpdateNoticeDisplayOrderRequest;
import com.danzzan.domain.notice.dto.request.UpdateNoticeRequest;
import com.danzzan.domain.notice.dto.response.EmergencyNoticeResponse;
import com.danzzan.domain.notice.dto.response.NoticeImagePresignResponse;
import com.danzzan.domain.notice.dto.response.NoticeImageUploadResponse;
import com.danzzan.domain.notice.dto.response.NoticeResponse;
import com.danzzan.domain.notice.service.EmergencyNoticeService;
import com.danzzan.domain.notice.service.NoticeService;
import com.danzzan.infra.s3.S3PresignService;
import com.danzzan.infra.s3.S3PresignedPutResult;
import com.danzzan.infra.s3.S3UploadResult;
import com.danzzan.infra.s3.S3Uploader;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminNoticeController {

    private final EmergencyNoticeService emergencyNoticeService;
    private final NoticeService noticeService;
    private final S3Uploader s3Uploader;
    private final S3PresignService s3PresignService;

    /** 긴급 공지 조회 (한 줄 메시지, 단일 레코드) */
    @GetMapping("/emergency")
    public ResponseEntity<EmergencyNoticeResponse> getEmergency() {
        return ResponseEntity.ok(emergencyNoticeService.get());
    }

    /** 긴급 공지 수정 */
    @PutMapping("/emergency")
    public ResponseEntity<EmergencyNoticeResponse> updateEmergency(
            @Valid @RequestBody UpdateEmergencyRequest request) {
        return ResponseEntity.ok(emergencyNoticeService.update(request));
    }

    /** 일반 공지 목록 (검색 + 카테고리 필터, 페이지네이션) */
    @GetMapping("/notices")
    public ResponseEntity<Page<NoticeResponse>> getNotices(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false, defaultValue = "ACTIVE") String status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(noticeService.getAdminNotices(keyword, category, status, pageable));
    }

    /** 공지 생성 */
    @PostMapping("/notices")
    public ResponseEntity<NoticeResponse> createNotice(@Valid @RequestBody CreateNoticeRequest request) {
        return ResponseEntity.ok(noticeService.create(request));
    }

    /** 공지 수정 */
    @PutMapping("/notices/{id}")
    public ResponseEntity<NoticeResponse> updateNotice(
            @PathVariable Long id,
            @Valid @RequestBody UpdateNoticeRequest request) {
        return ResponseEntity.ok(noticeService.update(id, request));
    }

    /** 공지 삭제 (소프트 삭제: isActive=false) */
    @DeleteMapping("/notices/{id}")
    public ResponseEntity<Void> deleteNotice(@PathVariable Long id) {
        noticeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 삭제된 공지 복구(활성화).
     * 삭제됨 탭에서 "활성화" 버튼 클릭 시 호출. isActive=true 로 복구됩니다.
     */
    @PatchMapping("/notices/{id}/restore")
    public ResponseEntity<NoticeResponse> restoreNotice(@PathVariable Long id) {
        return ResponseEntity.ok(noticeService.restore(id));
    }

    /**
     * 공지 displayOrder 일괄 업데이트.
     * 프론트에서 [{id, displayOrder}] 배열을 보내면 해당 공지들의 displayOrder만 갱신합니다.
     * 주로 isPinned=true 섹션(핀 공지) 내에서 드래그 앤 드롭한 결과를 저장하는 데 사용합니다.
     */
    @PutMapping("/notices/display-order")
    public ResponseEntity<Void> updateDisplayOrders(
            @Valid @RequestBody UpdateNoticeDisplayOrderRequest request
    ) {
        noticeService.updateDisplayOrders(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * 공지 대표 이미지 업로드 (S3).
     * form-data: file
     */
    @PostMapping("/notices/images")
    public ResponseEntity<NoticeImageUploadResponse> uploadNoticeImage(@RequestPart("file") MultipartFile file) {
        S3UploadResult result = s3Uploader.uploadNoticeImage(file);
        return ResponseEntity.ok(NoticeImageUploadResponse.from(result));
    }

    /**
     * 공지 대표 이미지 업로드용 Presigned URL 발급 (S3).
     * 클라이언트가 받은 uploadUrl로 PUT 업로드 후, imageUrl을 Notice.thumbnailImageUrl 등에 저장해서 사용.
     */
    @PostMapping("/notices/images/presign")
    public ResponseEntity<NoticeImagePresignResponse> presignNoticeImage(@Valid @RequestBody PresignNoticeImageRequest request) {
        S3PresignedPutResult result = s3PresignService.presignPutNoticeImage(
                request.getFileName(),
                request.getContentType(),
                request.getFileSize()
        );
        return ResponseEntity.ok(NoticeImagePresignResponse.from(result));
    }
}
