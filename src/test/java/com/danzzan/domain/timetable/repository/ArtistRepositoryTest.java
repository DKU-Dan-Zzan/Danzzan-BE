package com.danzzan.domain.timetable.repository;

import com.danzzan.domain.timetable.model.entity.Artist;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ArtistRepository의 백필 대상 조회 쿼리를 실제 DB에 대해 검증한다.
 * description은 선택 입력이라 null일 수 있는데, 그 사실만으로
 * 보정 대상에 걸리면 안 된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:artist-repository;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ArtistRepositoryTest {

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void 한국어_이름이_있고_영문_이름이_비어있으면_보정_대상으로_조회된다() {
        Artist artist = Artist.create("아티스트", "소개", null);
        entityManager.persistAndFlush(artist);

        List<Artist> result = artistRepository.findNeedingTranslation(PageRequest.of(0, 50));

        assertThat(result).extracting(Artist::getId).contains(artist.getId());
    }

    @Test
    void 한국어_소개가_없고_영문_소개도_없는_행은_그_사실만으로는_조회되지_않는다() {
        Artist artist = Artist.create("아티스트", null, null);
        artist.applyTranslation("Artist", null);
        entityManager.persistAndFlush(artist);

        List<Artist> result = artistRepository.findNeedingTranslation(PageRequest.of(0, 50));

        assertThat(result).extracting(Artist::getId).doesNotContain(artist.getId());
    }

    @Test
    void 결과는_id_오름차순으로_결정적으로_정렬된다() {
        Artist third = Artist.create("세번째", null, null);
        Artist first = Artist.create("첫번째", null, null);
        Artist second = Artist.create("두번째", null, null);
        entityManager.persistAndFlush(third);
        entityManager.persistAndFlush(first);
        entityManager.persistAndFlush(second);

        List<Artist> result = artistRepository.findNeedingTranslation(PageRequest.of(0, 50));

        assertThat(result).extracting(Artist::getId).isSorted();
    }
}
