package com.danzzan.domain.notice.service;

import com.danzzan.domain.notice.dto.request.CreateNoticeRequest;
import com.danzzan.domain.notice.dto.request.UpdateNoticeRequest;
import com.danzzan.domain.notice.dto.response.NoticeResponse;
import com.danzzan.domain.notice.entity.Notice;
import com.danzzan.domain.notice.repository.NoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeRepository noticeRepository;

    @Transactional(readOnly = true)
    public Page<NoticeResponse> getActiveNotices(String keyword, String category, Pageable pageable) {
        String normalizedKeyword = normalizeOptional(keyword);
        String normalizedCategory = normalizeOptional(category);
        Page<Notice> page = noticeRepository.searchActive(normalizedKeyword, normalizedCategory, pageable);
        return page.map(NoticeResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<NoticeResponse> getNotices(String keyword, Pageable pageable) {
        Page<Notice> page = StringUtils.hasText(keyword)
                ? noticeRepository.findByTitleContainingAndIsActiveTrue(keyword.trim(), pageable)
                : noticeRepository.findByIsActiveTrue(pageable);
        return page.map(NoticeResponse::from);
    }

    @Transactional(readOnly = true)
    public NoticeResponse getNotice(Long id) {
        Notice notice = noticeRepository.findActiveById(id)
                .orElseThrow(() -> new IllegalArgumentException("공지를 찾을 수 없습니다. id=" + id));
        return NoticeResponse.from(notice);
    }

    @Transactional
    public NoticeResponse create(CreateNoticeRequest request) {
        if (Boolean.TRUE.equals(request.getIsEmergency())) {
            clearOtherEmergencyFlags();
        }
        Notice notice = Notice.create(
                request.getTitle(),
                request.getContent(),
                request.getAuthor(),
                request.getIsEmergency()
        );
        return NoticeResponse.from(noticeRepository.save(notice));
    }

    @Transactional
    public NoticeResponse update(Long id, UpdateNoticeRequest request) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("공지를 찾을 수 없습니다. id=" + id));
        if (Boolean.TRUE.equals(request.getIsEmergency())) {
            clearOtherEmergencyFlags();
        }
        notice.setTitle(request.getTitle());
        notice.setContent(request.getContent());
        notice.setAuthor(request.getAuthor());
        notice.setIsEmergency(Boolean.TRUE.equals(request.getIsEmergency()));
        return NoticeResponse.from(noticeRepository.save(notice));
    }

    @Transactional
    public void delete(Long id) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("공지를 찾을 수 없습니다. id=" + id));
        notice.setIsActive(false);
        noticeRepository.save(notice);
    }

    private void clearOtherEmergencyFlags() {
        for (Notice n : noticeRepository.findByIsEmergencyTrue()) {
            n.setIsEmergency(false);
            noticeRepository.save(n);
        }
    }

    private String normalizeOptional(String s) {
        if (s == null) {
            return null;
        }
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static final class StringUtils {
        static boolean hasText(String s) {
            return s != null && !s.isBlank();
        }
    }
}
