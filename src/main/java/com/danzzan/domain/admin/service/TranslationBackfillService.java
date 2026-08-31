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

import java.util.List;
import java.util.function.IntSupplier;

/**
 * 영문이 비어 있는 행을 주기적으로 찾아 번역해 채운다.
 *
 * <p>이 클래스에는 트랜잭션이 걸려 있지 않다. 의도된 것이다. 저장은
 * {@link TranslationBackfillWriter}가 행 단위로 처리하고, 이 클래스는 번역
 * 호출과 실패 흡수만 맡는다. 그렇게 나눈 이유가 두 가지다.</p>
 *
 * <ol>
 *   <li><b>한 행의 실패가 회차 전체를 날리지 않게</b> — 예전에는 이 메서드
 *       전체가 하나의 트랜잭션이라, 마지막 행에서 터져도 앞서 번역한 100여 개가
 *       함께 롤백됐다. 5분 뒤 같은 행을 다시 번역하니 요금만 나가고 저장은
 *       영원히 되지 않았다. 실제로 이 구조가 DeepL 무료 쿼터를 소진시켰다.</li>
 *   <li><b>외부 HTTP를 트랜잭션 밖에서 하도록</b> — DeepL 호출은 블로킹이다.
 *       트랜잭션 안에서 기다리면 그동안 DB 커넥션을 붙잡는다. 한 회차에 최대
 *       300건을 호출하므로 커넥션 점유 시간이 길어진다.</li>
 * </ol>
 */
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
    private final TranslationBackfillWriter writer;

    @Scheduled(fixedDelayString = "${translation.backfill.fixed-delay-ms:300000}")
    public int backfillAll() {
        int filled = 0;

        filled += save("공지", null, noticeBackfillService::backfill);
        filled += backfillBooths();
        filled += backfillPubs();
        filled += backfillColleges();
        filled += backfillArtists();
        filled += backfillPerformances();
        filled += backfillEmergencyNotices();

        log.info("전체 번역 보정 완료. 처리={}", filled);
        return filled;
    }

    private int backfillBooths() {
        int filled = 0;
        for (var booth : boothRepository.findNeedingTranslation(PageRequest.of(0, BATCH_SIZE))) {
            List<String> t = translationService.translateAll(
                    List.of(safe(booth.getNameEn(), booth.getName()),
                            safe(booth.getDescriptionEn(), booth.getDescription())));
            filled += save("부스", booth.getId(),
                    () -> writer.applyBooth(booth.getId(), t.get(0), t.get(1)));
        }
        return filled;
    }

    private int backfillPubs() {
        int filled = 0;
        for (var pub : pubRepository.findNeedingTranslation(PageRequest.of(0, BATCH_SIZE))) {
            List<String> t = translationService.translateAll(
                    List.of(safe(pub.getNameEn(), pub.getName()),
                            safe(pub.getIntroEn(), pub.getIntro()),
                            safe(pub.getDescriptionEn(), pub.getDescription()),
                            safe(pub.getDepartmentEn(), pub.getDepartment())));
            filled += save("주점", pub.getId(),
                    () -> writer.applyPub(pub.getId(), t.get(0), t.get(1), t.get(2), t.get(3)));
        }
        return filled;
    }

    private int backfillColleges() {
        int filled = 0;
        for (var college : collegeRepository.findTop50ByNameEnIsNull()) {
            String nameEn = translationService.translate(college.getName());
            filled += save("단과대", college.getId(),
                    () -> writer.applyCollege(college.getId(), nameEn));
        }
        return filled;
    }

    private int backfillArtists() {
        int filled = 0;
        for (var artist : artistRepository.findNeedingTranslation(PageRequest.of(0, BATCH_SIZE))) {
            List<String> t = translationService.translateAll(
                    List.of(safe(artist.getNameEn(), artist.getName()),
                            safe(artist.getDescriptionEn(), artist.getDescription())));
            filled += save("아티스트", artist.getId(),
                    () -> writer.applyArtist(artist.getId(), t.get(0), t.get(1)));
        }
        return filled;
    }

    private int backfillPerformances() {
        int filled = 0;
        for (var performance : performanceRepository.findNeedingTranslation(PageRequest.of(0, BATCH_SIZE))) {
            String stageEn = translationService.translate(performance.getStage());
            filled += save("공연", performance.getId(),
                    () -> writer.applyPerformance(performance.getId(), stageEn));
        }
        return filled;
    }

    /**
     * 긴급공지는 홈 최상단에 뜨고 우천 중단 같은 내용이 올라간다. 번역이 비어
     * 있으면 외국인이 그 경고를 놓치므로 보정 대상에 포함한다.
     */
    private int backfillEmergencyNotices() {
        int filled = 0;
        for (var emergency : emergencyNoticeRepository.findNeedingTranslation()) {
            String messageEn = translationService.translate(emergency.getMessage());
            filled += save("긴급공지", emergency.getId(),
                    () -> writer.applyEmergencyNotice(emergency.getId(), messageEn));
        }
        return filled;
    }

    /**
     * 저장 한 건을 실행하고, 실패하면 그 행만 건너뛴다.
     *
     * <p>여기서 예외를 삼키는 것이 이 클래스의 핵심이다. 던지면 남은 행이 모두
     * 처리되지 않고, 다음 회차에 같은 행부터 다시 번역하게 된다.</p>
     */
    private int save(String kind, Object id, IntSupplier work) {
        try {
            return work.getAsInt();
        } catch (Exception e) {
            log.warn("{} 번역 저장에 실패했습니다. 이 행만 건너뛰고 계속합니다. id={}", kind, id, e);
            return 0;
        }
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
