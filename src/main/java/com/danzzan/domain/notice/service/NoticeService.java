package com.danzzan.domain.notice.service;

import com.danzzan.domain.notice.dto.request.CreateNoticeRequest;
import com.danzzan.domain.notice.dto.request.UpdateNoticeDisplayOrderRequest;
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
    public Page<NoticeResponse> getAdminNotices(String keyword, String category, String status, Pageable pageable) {
        String normalizedKeyword = normalizeOptional(keyword);
        String normalizedCategory = normalizeOptional(category);
        Boolean isActiveFilter = mapStatusToIsActive(status);
        Page<Notice> page = noticeRepository.searchByStatus(normalizedKeyword, normalizedCategory, isActiveFilter, pageable);
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
        Notice notice = Notice.create(
                request.getTitle(),
                request.getContent(),
                request.getAuthor(),
                request.getCategory(),
                request.getIsPinned(),
                request.getThumbnailImageUrl(),
                request.getImages(),
                false
        );
        return NoticeResponse.from(noticeRepository.save(notice));
    }

    @Transactional
    public NoticeResponse update(Long id, UpdateNoticeRequest request) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("공지를 찾을 수 없습니다. id=" + id));
        notice.setTitle(request.getTitle());
        notice.setContent(request.getContent());
        notice.setAuthor(request.getAuthor());
        notice.setCategory((request.getCategory() == null || request.getCategory().isBlank())
                ? "GENERAL"
                : request.getCategory().trim());
        notice.setIsPinned(Boolean.TRUE.equals(request.getIsPinned()));
        // 이미지 목록 전체 교체
        notice.getImages().clear();
        if (request.getImages() != null) {
            notice.getImages().addAll(request.getImages());
        }
        // 썸네일 정책: 명시된 썸네일이 없고 이미지가 있으면 첫 번째 이미지를 썸네일로 사용
        String thumbnail = request.getThumbnailImageUrl();
        if ((thumbnail == null || thumbnail.isBlank())
                && !notice.getImages().isEmpty()) {
            notice.setThumbnailImageUrl(notice.getImages().get(0));
        } else {
            notice.setThumbnailImageUrl(thumbnail);
        }
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

    @Transactional
    public NoticeResponse restore(Long id) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("공지를 찾을 수 없습니다. id=" + id));
        notice.setIsActive(true);
        return NoticeResponse.from(noticeRepository.save(notice));
    }

    @Transactional
    public void updateDisplayOrders(UpdateNoticeDisplayOrderRequest request) {
        for (UpdateNoticeDisplayOrderRequest.Item item : request.getOrders()) {
            Notice notice = noticeRepository.findById(item.getId())
                    .orElseThrow(() -> new IllegalArgumentException("공지를 찾을 수 없습니다. id=" + item.getId()));
            notice.setDisplayOrder(item.getDisplayOrder());
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

    private Boolean mapStatusToIsActive(String status) {
        if (status == null || status.isBlank()) {
            return Boolean.TRUE;
        }
        String upper = status.trim().toUpperCase();
        return switch (upper) {
            case "ACTIVE" -> Boolean.TRUE;
            case "DELETED" -> Boolean.FALSE;
            case "ALL" -> null;
            default -> Boolean.TRUE;
        };
    }
}
