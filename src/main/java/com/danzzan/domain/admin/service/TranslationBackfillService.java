package com.danzzan.domain.admin.service;

import com.danzzan.domain.boothmap.repository.BoothRepository;
import com.danzzan.domain.boothmap.repository.CollegeRepository;
import com.danzzan.domain.boothmap.repository.PubRepository;
import com.danzzan.domain.notice.service.NoticeTranslationBackfillService;
import com.danzzan.domain.timetable.repository.ArtistRepository;
import com.danzzan.domain.timetable.repository.PerformanceRepository;
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
public class TranslationBackfillService {

    private final NoticeTranslationBackfillService noticeBackfillService;
    private final BoothRepository boothRepository;
    private final PubRepository pubRepository;
    private final CollegeRepository collegeRepository;
    private final ArtistRepository artistRepository;
    private final PerformanceRepository performanceRepository;
    private final TranslationService translationService;

    @Scheduled(fixedDelayString = "${translation.backfill.fixed-delay-ms:300000}")
    @Transactional
    public int backfillAll() {
        int filled = noticeBackfillService.backfill();

        for (var booth : boothRepository.findTop50ByNameEnIsNullOrDescriptionEnIsNull()) {
            List<String> t = translationService.translateAll(
                    List.of(safe(booth.getNameEn(), booth.getName()),
                            safe(booth.getDescriptionEn(), booth.getDescription())));
            booth.applyTranslation(t.get(0), t.get(1));
            filled++;
        }

        for (var pub : pubRepository
                .findTop50ByNameEnIsNullOrIntroEnIsNullOrDescriptionEnIsNullOrDepartmentEnIsNull()) {
            List<String> t = translationService.translateAll(
                    List.of(safe(pub.getNameEn(), pub.getName()),
                            safe(pub.getIntroEn(), pub.getIntro()),
                            safe(pub.getDescriptionEn(), pub.getDescription()),
                            safe(pub.getDepartmentEn(), pub.getDepartment())));
            pub.applyTranslation(t.get(0), t.get(1), t.get(2), t.get(3));
            filled++;
        }

        for (var college : collegeRepository.findTop50ByNameEnIsNull()) {
            college.applyTranslation(translationService.translate(college.getName()));
            filled++;
        }

        for (var artist : artistRepository.findTop50ByNameEnIsNullOrDescriptionEnIsNull()) {
            List<String> t = translationService.translateAll(
                    List.of(safe(artist.getNameEn(), artist.getName()),
                            safe(artist.getDescriptionEn(), artist.getDescription())));
            artist.applyTranslation(t.get(0), t.get(1));
            filled++;
        }

        for (var performance : performanceRepository.findTop50ByStageEnIsNull()) {
            performance.applyTranslation(translationService.translate(performance.getStage()));
            filled++;
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
