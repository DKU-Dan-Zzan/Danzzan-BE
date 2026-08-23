package com.danzzan.domain.notice.service;

import com.danzzan.domain.notice.dto.request.CreateNoticeRequest;
import com.danzzan.domain.notice.dto.request.UpdateNoticeDisplayOrderRequest;
import com.danzzan.domain.notice.dto.request.UpdateNoticeRequest;
import com.danzzan.domain.notice.dto.response.NoticeResponse;
import com.danzzan.domain.notice.entity.Notice;
import com.danzzan.domain.notice.repository.NoticeRepository;
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
        notice.applyTranslation(translated.get(0), translated.get(1));

        boolean hasManualEnglish = isSupplied(request.getTitleEn()) || isSupplied(request.getContentEn());

        // 수동 입력 중 빈 값은 방금 채운 자동번역 결과를 그대로 남긴다.
        if (hasManualEnglish) {
            String titleEn = manualOrAuto(request.getTitleEn(), notice.getTitleEn());
            String contentEn = manualOrAuto(request.getContentEn(), notice.getContentEn());
            notice.applyManualTranslation(titleEn, contentEn);
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

        // 요청이 이긴다: 이번 요청에서 해당 영문 칸을 비워뒀다(=자동번역을 원한다)는 뜻이고,
        // 그 언어의 한국어가 실제로 바뀌었다면, 엔티티에 남아있는 예전 enIsManual/영문 값은
        // 낡은 정보이므로 지운다. 지워야 applyTranslation의 필드별 가드(“null이면 채운다”)가
        // 새로 번역한 값을 채워 넣을 수 있다. 한국어가 바뀌지 않았다면 지우지 않는다 — 그러면
        // 관련 없는 필드 수정만으로도 매번 DeepL 재번역이 발생하게 된다.
        if (!isSupplied(request.getTitleEn()) && titleKoreanChanged) {
            notice.setTitleEn(null);
        }
        if (!isSupplied(request.getContentEn()) && contentKoreanChanged) {
            notice.setContentEn(null);
        }

        // 한국어가 바뀐 경우에 한해 자동 재번역한다 (koreanChanged 가드 유지).
        if (koreanChanged) {
            List<String> retranslated = translationService.translateAll(
                    List.of(
                            request.getTitle() == null ? "" : request.getTitle(),
                            request.getContent() == null ? "" : request.getContent()
                    )
            );
            notice.applyTranslation(retranslated.get(0), retranslated.get(1));
        }

        boolean hasManualEnglish = isSupplied(request.getTitleEn()) || isSupplied(request.getContentEn());

        // 수동 입력이 최우선이다: 한국어 변경 여부와 무관하게 관리자가 직접 쓴 영문을 반영한다.
        // 수동 입력 중 빈 값은 (방금 자동번역했거나 기존에 남아있던) 현재 값을 그대로 남긴다.
        if (hasManualEnglish) {
            String titleEn = manualOrAuto(request.getTitleEn(), notice.getTitleEn());
            String contentEn = manualOrAuto(request.getContentEn(), notice.getContentEn());
            notice.applyManualTranslation(titleEn, contentEn);
        }

        return NoticeResponse.from(noticeRepository.save(notice));
    }

    /**
     * 관리자가 해당 칸에 값을 채웠는지 여부. 비어 있으면 "자동번역해 달라"는 뜻이다.
     */
    private static boolean isSupplied(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 관리자가 직접 입력한 값이 있으면 그 값을, 없으면 자동으로 채워진 값을 사용한다.
     */
    private static String manualOrAuto(String manual, String auto) {
        return isSupplied(manual) ? manual : auto;
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
