package com.danzzan.domain.notice.service;

import com.danzzan.domain.notice.entity.Notice;
import com.danzzan.domain.notice.repository.NoticeRepository;
import com.danzzan.infra.translation.TranslationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeTranslationBackfillService {

    private final NoticeRepository noticeRepository;
    private final TranslationService translationService;

    /**
     * 영문이 비어 있는 공지를 주기적으로 채운다.
     * 이미 값이 있는 필드는 건드리지 않으므로 사람이 쓴 번역이 덮어써지지 않는다.
     */
    @Scheduled(fixedDelayString = "${translation.backfill.fixed-delay-ms:300000}")
    @Transactional
    public int backfill() {
        List<Notice> targets = noticeRepository.findTop50ByTitleEnIsNullOrContentEnIsNull();
        if (targets.isEmpty()) {
            return 0;
        }

        int filled = 0;
        for (Notice notice : targets) {
            List<String> translated = translationService.translateAll(
                    List.of(
                            notice.getTitleEn() == null ? safe(notice.getTitle()) : "",
                            notice.getContentEn() == null ? safe(notice.getContent()) : ""
                    )
            );
            notice.applyTranslation(translated.get(0), translated.get(1));
            if (notice.getTitleEn() != null || notice.getContentEn() != null) {
                filled++;
            }
        }

        log.info("공지 번역 보정 완료. 대상={}, 채움={}", targets.size(), filled);
        return filled;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
