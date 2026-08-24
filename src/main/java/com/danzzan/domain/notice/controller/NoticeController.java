package com.danzzan.domain.notice.controller;

import com.danzzan.domain.notice.dto.response.NoticeResponse;
import com.danzzan.domain.notice.service.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    /** 공지사항 목록 (검색 + 카테고리 필터) */
    @GetMapping
    public ResponseEntity<Page<NoticeResponse>> getNotices(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(name = "lang", required = false, defaultValue = "ko") String lang,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        boolean english = "en".equalsIgnoreCase(lang);
        return ResponseEntity.ok(noticeService.getActiveNotices(keyword, category, english, pageable));
    }

    /** 공지사항 상세 */
    @GetMapping("/{id}")
    public ResponseEntity<NoticeResponse> getNotice(
            @PathVariable Long id,
            @RequestParam(name = "lang", required = false, defaultValue = "ko") String lang
    ) {
        boolean english = "en".equalsIgnoreCase(lang);
        return ResponseEntity.ok(noticeService.getNotice(id, english));
    }
}

