package com.danzzan.domain.timetable.repository;

import com.danzzan.domain.timetable.model.entity.Artist;
import com.danzzan.domain.timetable.model.entity.Performance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PerformanceRepository의 백필 대상 조회 쿼리를 실제 DB에 대해 검증한다.
 * stage는 선택 입력이라 null일 수 있는데, 그 사실만으로
 * 보정 대상에 걸리면 안 된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:performance-repository;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PerformanceRepositoryTest {

    @Autowired
    private PerformanceRepository performanceRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Artist artist() {
        return entityManager.persistAndFlush(Artist.create("아티스트", null, null));
    }

    private Performance performance(Artist artist, String stage) {
        return Performance.create(LocalDate.of(2026, 8, 29), LocalTime.of(12, 0), LocalTime.of(13, 0), artist, stage);
    }

    @Test
    void 한국어_스테이지가_있고_영문_스테이지가_비어있으면_보정_대상으로_조회된다() {
        Performance performance = entityManager.persistAndFlush(performance(artist(), "메인 스테이지"));

        List<Performance> result = performanceRepository.findNeedingTranslation(PageRequest.of(0, 50));

        assertThat(result).extracting(Performance::getId).contains(performance.getId());
    }

    @Test
    void 한국어_스테이지가_없고_영문_스테이지도_없는_행은_그_사실만으로는_조회되지_않는다() {
        Performance performance = entityManager.persistAndFlush(performance(artist(), null));

        List<Performance> result = performanceRepository.findNeedingTranslation(PageRequest.of(0, 50));

        assertThat(result).extracting(Performance::getId).doesNotContain(performance.getId());
    }

    @Test
    void 결과는_id_오름차순으로_결정적으로_정렬된다() {
        Artist artist = artist();
        Performance third = entityManager.persistAndFlush(performance(artist, "세번째"));
        Performance first = entityManager.persistAndFlush(performance(artist, "첫번째"));
        Performance second = entityManager.persistAndFlush(performance(artist, "두번째"));

        List<Performance> result = performanceRepository.findNeedingTranslation(PageRequest.of(0, 50));

        assertThat(result).extracting(Performance::getId).isSorted();
    }
}
