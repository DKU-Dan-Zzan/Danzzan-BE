package com.danzzan.domain.admin.service;

import com.danzzan.domain.boothmap.repository.BoothRepository;
import com.danzzan.domain.boothmap.repository.CollegeRepository;
import com.danzzan.domain.boothmap.repository.PubRepository;
import com.danzzan.domain.notice.repository.EmergencyNoticeRepository;
import com.danzzan.domain.notice.service.NoticeTranslationBackfillService;
import com.danzzan.domain.timetable.repository.ArtistRepository;
import com.danzzan.domain.timetable.repository.PerformanceRepository;
import com.danzzan.infra.translation.TranslationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationBackfillService {

    private static final int BATCH_SIZE = 50;

    private final NoticeTranslationBackfillService noticeBackfillService;
    private final BoothRepository boothRepository;
    private final PubRepository pubRepository;
    private final CollegeRepository collegeRepository;
    private final ArtistRepository artistRepository;
    private final PerformanceRepository performanceRepository;
    private final EmergencyNoticeRepository emergencyNoticeRepository;
    private final TranslationService translationService;

    @Scheduled(fixedDelayString = "${translation.backfill.fixed-delay-ms:300000}")
    @Transactional
    public int backfillAll() {
        int filled = noticeBackfillService.backfill();

        for (var booth : boothRepository.findNeedingTranslation(PageRequest.of(0, BATCH_SIZE))) {
            boolean nameWasBlank = booth.getNameEn() == null;
            boolean descriptionWasBlank = booth.getDescriptionEn() == null;

            List<String> t = translationService.translateAll(
                    List.of(safe(booth.getNameEn(), booth.getName()),
                            safe(booth.getDescriptionEn(), booth.getDescription())));
            booth.applyTranslation(t.get(0), t.get(1));

            if ((nameWasBlank && booth.getNameEn() != null)
                    || (descriptionWasBlank && booth.getDescriptionEn() != null)) {
                filled++;
            }
        }

        for (var pub : pubRepository.findNeedingTranslation(PageRequest.of(0, BATCH_SIZE))) {
            boolean nameWasBlank = pub.getNameEn() == null;
            boolean introWasBlank = pub.getIntroEn() == null;
            boolean descriptionWasBlank = pub.getDescriptionEn() == null;
            boolean departmentWasBlank = pub.getDepartmentEn() == null;

            List<String> t = translationService.translateAll(
                    List.of(safe(pub.getNameEn(), pub.getName()),
                            safe(pub.getIntroEn(), pub.getIntro()),
                            safe(pub.getDescriptionEn(), pub.getDescription()),
                            safe(pub.getDepartmentEn(), pub.getDepartment())));
            pub.applyTranslation(t.get(0), t.get(1), t.get(2), t.get(3));

            if ((nameWasBlank && pub.getNameEn() != null)
                    || (introWasBlank && pub.getIntroEn() != null)
                    || (descriptionWasBlank && pub.getDescriptionEn() != null)
                    || (departmentWasBlank && pub.getDepartmentEn() != null)) {
                filled++;
            }
        }

        for (var college : collegeRepository.findTop50ByNameEnIsNull()) {
            boolean nameWasBlank = college.getNameEn() == null;
            college.applyTranslation(translationService.translate(college.getName()));
            if (nameWasBlank && college.getNameEn() != null) {
                filled++;
            }
        }

        for (var artist : artistRepository.findNeedingTranslation(PageRequest.of(0, BATCH_SIZE))) {
            boolean nameWasBlank = artist.getNameEn() == null;
            boolean descriptionWasBlank = artist.getDescriptionEn() == null;

            List<String> t = translationService.translateAll(
                    List.of(safe(artist.getNameEn(), artist.getName()),
                            safe(artist.getDescriptionEn(), artist.getDescription())));
            artist.applyTranslation(t.get(0), t.get(1));

            if ((nameWasBlank && artist.getNameEn() != null)
                    || (descriptionWasBlank && artist.getDescriptionEn() != null)) {
                filled++;
            }
        }

        for (var performance : performanceRepository.findNeedingTranslation(PageRequest.of(0, BATCH_SIZE))) {
            boolean stageWasBlank = performance.getStageEn() == null;
            performance.applyTranslation(translationService.translate(performance.getStage()));
            if (stageWasBlank && performance.getStageEn() != null) {
                filled++;
            }
        }

        // 긴급공지는 홈 최상단에 뜨고 우천 중단 같은 내용이 올라간다. 번역이 비어
        // 있으면 외국인이 그 경고를 놓치므로 보정 대상에 포함한다.
        for (var emergency : emergencyNoticeRepository.findNeedingTranslation()) {
            String translated = translationService.translate(emergency.getMessage());
            if (translated != null) {
                emergency.setMessageEn(translated);
                filled++;
            }
        }

        log.info("전체 번역 보정 완료. 처리={}", filled);
        return filled;
    }

    /**
     * 이미 영문이 채워진 필드는 빈 문자열을 넘겨 DeepL 호출에서 제외한다.
     */
    private String safe(String existingEnglish, String korean) {
        if (existingEnglish != null) {
            return "";
        }
        return korean == null ? "" : korean;
    }
}
