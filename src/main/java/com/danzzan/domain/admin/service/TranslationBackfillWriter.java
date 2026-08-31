package com.danzzan.domain.admin.service;

import com.danzzan.domain.boothmap.repository.BoothRepository;
import com.danzzan.domain.boothmap.repository.CollegeRepository;
import com.danzzan.domain.boothmap.repository.PubRepository;
import com.danzzan.domain.notice.repository.EmergencyNoticeRepository;
import com.danzzan.domain.timetable.repository.ArtistRepository;
import com.danzzan.domain.timetable.repository.PerformanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 번역 결과를 행 하나마다 독립된 트랜잭션으로 저장한다.
 *
 * <p>이전에는 {@code TranslationBackfillService.backfillAll()} 전체가 하나의
 * 트랜잭션이었다. 그래서 행 하나가 저장에 실패하면 그 회차에 번역한 모든 행이
 * 함께 롤백됐고, 5분 뒤 같은 행들을 처음부터 다시 번역했다. <b>번역 요금은 매번
 * 나가는데 저장은 영원히 되지 않는 구조</b>였다.</p>
 *
 * <p>실제로 그 일이 일어났다. {@code booth.description_en}이 {@code VARCHAR(255)}
 * 였는데 한국어를 영어로 옮기면 글자 수가 늘어 255를 넘겼고, MySQL이 커밋 시점에
 * 잘림 오류를 냈다. 같은 115개 필드를 136회 재번역한 끝에 DeepL 무료 쿼터
 * 100만 자가 소진됐다. 컬럼 길이는 늘렸지만, 길이만 고치면 다음에 다른 이유로
 * 한 행이 실패했을 때 같은 일이 반복된다.</p>
 *
 * <p>행 단위로 트랜잭션을 끊으면 실패한 행만 건너뛰고 나머지는 저장된다. 호출부는
 * 각 호출을 try/catch로 감싸 실패를 흡수한다.</p>
 *
 * <p>번역(DeepL 호출)은 이 클래스 밖에서 끝난 뒤 결과만 넘어온다. 외부 HTTP를
 * 트랜잭션 안에서 기다리면 그동안 DB 커넥션을 붙잡고 있게 되기 때문이다.</p>
 */
@Service
@RequiredArgsConstructor
public class TranslationBackfillWriter {

    private final BoothRepository boothRepository;
    private final PubRepository pubRepository;
    private final CollegeRepository collegeRepository;
    private final ArtistRepository artistRepository;
    private final PerformanceRepository performanceRepository;
    private final EmergencyNoticeRepository emergencyNoticeRepository;

    @Transactional
    public int applyBooth(Long id, String nameEn, String descriptionEn) {
        return boothRepository.findById(id).map(booth -> {
            boolean nameWasBlank = booth.getNameEn() == null;
            boolean descriptionWasBlank = booth.getDescriptionEn() == null;

            booth.applyTranslation(nameEn, descriptionEn);

            return filledAny(nameWasBlank, booth.getNameEn())
                    || filledAny(descriptionWasBlank, booth.getDescriptionEn()) ? 1 : 0;
        }).orElse(0);
    }

    @Transactional
    public int applyPub(Long id, String nameEn, String introEn, String descriptionEn, String departmentEn) {
        return pubRepository.findById(id).map(pub -> {
            boolean nameWasBlank = pub.getNameEn() == null;
            boolean introWasBlank = pub.getIntroEn() == null;
            boolean descriptionWasBlank = pub.getDescriptionEn() == null;
            boolean departmentWasBlank = pub.getDepartmentEn() == null;

            pub.applyTranslation(nameEn, introEn, descriptionEn, departmentEn);

            return filledAny(nameWasBlank, pub.getNameEn())
                    || filledAny(introWasBlank, pub.getIntroEn())
                    || filledAny(descriptionWasBlank, pub.getDescriptionEn())
                    || filledAny(departmentWasBlank, pub.getDepartmentEn()) ? 1 : 0;
        }).orElse(0);
    }

    @Transactional
    public int applyCollege(Long id, String nameEn) {
        return collegeRepository.findById(id).map(college -> {
            boolean nameWasBlank = college.getNameEn() == null;
            college.applyTranslation(nameEn);
            return filledAny(nameWasBlank, college.getNameEn()) ? 1 : 0;
        }).orElse(0);
    }

    @Transactional
    public int applyArtist(Integer id, String nameEn, String descriptionEn) {
        return artistRepository.findById(id).map(artist -> {
            boolean nameWasBlank = artist.getNameEn() == null;
            boolean descriptionWasBlank = artist.getDescriptionEn() == null;

            artist.applyTranslation(nameEn, descriptionEn);

            return filledAny(nameWasBlank, artist.getNameEn())
                    || filledAny(descriptionWasBlank, artist.getDescriptionEn()) ? 1 : 0;
        }).orElse(0);
    }

    @Transactional
    public int applyPerformance(Integer id, String stageEn) {
        return performanceRepository.findById(id).map(performance -> {
            boolean stageWasBlank = performance.getStageEn() == null;
            performance.applyTranslation(stageEn);
            return filledAny(stageWasBlank, performance.getStageEn()) ? 1 : 0;
        }).orElse(0);
    }

    @Transactional
    public int applyEmergencyNotice(Long id, String messageEn) {
        if (messageEn == null) {
            return 0;
        }
        return emergencyNoticeRepository.findById(id).map(emergency -> {
            emergency.setMessageEn(messageEn);
            return 1;
        }).orElse(0);
    }

    /**
     * 비어 있던 필드가 이번에 실제로 채워졌는지 본다. 번역 실패 시
     * {@code TranslationService}가 null을 돌려주고 엔티티의 applyTranslation이
     * 그것을 무시하므로, 호출했다는 사실만으로는 채워졌다고 볼 수 없다.
     */
    private boolean filledAny(boolean wasBlank, String current) {
        return wasBlank && current != null;
    }
}
