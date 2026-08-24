package com.danzzan.domain.notice.service;

import com.danzzan.domain.notice.entity.Notice;
import com.danzzan.domain.notice.repository.NoticeRepository;
import com.danzzan.infra.translation.TranslationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    @Transactional
    public int backfill() {
        List<Notice> targets = noticeRepository.findTop50ByTitleEnIsNullOrContentEnIsNull();
        if (targets.isEmpty()) {
            return 0;
        }

        int filled = 0;
        for (Notice notice : targets) {
            boolean titleWasBlank = notice.getTitleEn() == null;
            boolean contentWasBlank = notice.getContentEn() == null;

            List<String> translated = translationService.translateAll(
                    List.of(
                            titleWasBlank ? safe(notice.getTitle()) : "",
                            contentWasBlank ? safe(notice.getContent()) : ""
                    )
            );
            notice.applyTranslation(translated.get(0), translated.get(1));

            boolean titleNewlyFilled = titleWasBlank && notice.getTitleEn() != null;
            boolean contentNewlyFilled = contentWasBlank && notice.getContentEn() != null;
            if (titleNewlyFilled || contentNewlyFilled) {
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
