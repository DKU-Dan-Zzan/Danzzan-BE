package com.danzzan.domain.notice.service;

import com.danzzan.domain.notice.dto.request.CreateNoticeRequest;
import com.danzzan.domain.notice.dto.request.UpdateNoticeDisplayOrderRequest;
import com.danzzan.domain.notice.dto.request.UpdateNoticeRequest;
import com.danzzan.domain.notice.dto.response.NoticeResponse;
import com.danzzan.domain.notice.entity.Notice;
import com.danzzan.domain.notice.repository.NoticeRepository;
import com.danzzan.infra.translation.FieldTranslationDecision;
import com.danzzan.infra.translation.TranslationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeRepository noticeRepository;
    private final TranslationService translationService;

    @Transactional(readOnly = true)
    public Page<NoticeResponse> getActiveNotices(String keyword, String category, boolean english, Pageable pageable) {
        String normalizedKeyword = normalizeOptional(keyword);
        String normalizedCategory = normalizeOptional(category);
        Page<Notice> page = noticeRepository.searchActive(normalizedKeyword, normalizedCategory, pageable);
        return page.map(notice -> NoticeResponse.from(notice, english));
    }

    @Transactional(readOnly = true)
    public Page<NoticeResponse> getNotices(String keyword, Pageable pageable) {
        Page<Notice> page = FieldTranslationDecision.isSupplied(keyword)
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
    public NoticeResponse getNotice(Long id, boolean english) {
        Notice notice = noticeRepository.findActiveById(id)
                .orElseThrow(() -> new IllegalArgumentException("공지를 찾을 수 없습니다. id=" + id));
        return NoticeResponse.from(notice, english);
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

        List<String> translated = translationService.translateAll(
                List.of(
                        request.getTitle() == null ? "" : request.getTitle(),
                        request.getContent() == null ? "" : request.getContent()
                )
        );

        // 생성 시에는 한국어가 늘 "새로 생겼다"고 보고(항상 변경), 저장된 영문은 늘 없다.
        notice.setTitleEn(FieldTranslationDecision.decideEnglish(
                true, request.getTitleEn(), notice.getTitleEn(), translated.get(0)));
        notice.setContentEn(FieldTranslationDecision.decideEnglish(
                true, request.getContentEn(), notice.getContentEn(), translated.get(1)));

        if (FieldTranslationDecision.isSupplied(request.getTitleEn())
                || FieldTranslationDecision.isSupplied(request.getContentEn())) {
            notice.setEnIsManual(true);
        }

        return NoticeResponse.from(noticeRepository.save(notice));
    }

    @Transactional
    public NoticeResponse update(Long id, UpdateNoticeRequest request) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("공지를 찾을 수 없습니다. id=" + id));
        String previousTitle = notice.getTitle();
        String previousContent = notice.getContent();

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

        boolean titleKoreanChanged = !Objects.equals(previousTitle, request.getTitle());
        boolean contentKoreanChanged = !Objects.equals(previousContent, request.getContent());
        boolean koreanChanged = titleKoreanChanged || contentKoreanChanged;

        // 한국어가 바뀐 경우에 한해 자동 재번역한다 (koreanChanged 가드 유지).
        String autoTitleEn = null;
        String autoContentEn = null;
        if (koreanChanged) {
            List<String> retranslated = translationService.translateAll(
                    List.of(
                            request.getTitle() == null ? "" : request.getTitle(),
                            request.getContent() == null ? "" : request.getContent()
                    )
            );
            autoTitleEn = retranslated.get(0);
            autoContentEn = retranslated.get(1);
        }

        // 지우기 → 자동 채움 → 수동 값 덮어쓰기, 이 세 단계의 순서가 곧 정답이다.
        // (FieldTranslationDecision 참고: 필드별 한국어 변경 여부로 지울지 정하고,
        // 지운 뒤에도 비어 있을 때만 자동번역으로 채우고, 관리자가 직접 쓴 값이 최종적으로 이긴다.)
        notice.setTitleEn(FieldTranslationDecision.decideEnglish(
                titleKoreanChanged, request.getTitleEn(), notice.getTitleEn(), autoTitleEn));
        notice.setContentEn(FieldTranslationDecision.decideEnglish(
                contentKoreanChanged, request.getContentEn(), notice.getContentEn(), autoContentEn));

        // 수동 플래그는 한 번 켜지면 유지된다: 이번 요청에 수동 입력이 하나라도 있으면 켜고,
        // 없으면 (되돌리지 않고) 기존 값을 그대로 둔다.
        if (FieldTranslationDecision.isSupplied(request.getTitleEn())
                || FieldTranslationDecision.isSupplied(request.getContentEn())) {
            notice.setEnIsManual(true);
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
