package com.danzzan.festival.domain.notice.controller;

import com.danzzan.festival.domain.notice.dto.request.CreateNoticeRequest;
import com.danzzan.festival.domain.notice.dto.request.UpdateEmergencyRequest;
import com.danzzan.festival.domain.notice.dto.request.UpdateNoticeRequest;
import com.danzzan.festival.domain.notice.dto.response.EmergencyNoticeResponse;
import com.danzzan.festival.domain.notice.dto.response.NoticeResponse;
import com.danzzan.festival.domain.notice.service.EmergencyNoticeService;
import com.danzzan.festival.domain.notice.service.NoticeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminNoticeController {

    private final EmergencyNoticeService emergencyNoticeService;
    private final NoticeService noticeService;

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

    /** 일반 공지 목록 (제목 검색, 페이지네이션) */
    @GetMapping("/notices")
    public ResponseEntity<Page<NoticeResponse>> getNotices(
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(noticeService.getNotices(keyword, pageable));
    }

    /** 공지 생성 (isEmergency=true 시 기존 긴급 공지 자동 해제) */
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
}
