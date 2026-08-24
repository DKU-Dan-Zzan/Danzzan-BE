package com.danzzan.domain.boothmap.repository;

import com.danzzan.domain.boothmap.model.entity.College;
import com.danzzan.domain.boothmap.model.entity.Pub;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PubRepository의 백필 대상 조회 쿼리를 실제 DB에 대해 검증한다.
 * intro/description은 선택 입력이라 null일 수 있는데, 그 사실만으로
 * 보정 대상에 걸리면 안 된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:pub-repository;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PubRepositoryTest {

    @Autowired
    private PubRepository pubRepository;

    @Autowired
    private TestEntityManager entityManager;

    private College college() {
        College college = BeanUtils.instantiateClass(College.class);
        ReflectionTestUtils.setField(college, "name", "공과대학");
        ReflectionTestUtils.setField(college, "locationX", 0.0);
        ReflectionTestUtils.setField(college, "locationY", 0.0);
        return entityManager.persistAndFlush(college);
    }

    @Test
    void 한국어_이름이_있고_영문_이름이_비어있으면_보정_대상으로_조회된다() {
        Pub pub = new Pub(college(), "컴퓨터공학과", "주점 이름", "한줄소개", "설명", null);
        entityManager.persistAndFlush(pub);

        List<Pub> result = pubRepository.findNeedingTranslation(PageRequest.of(0, 50));

        assertThat(result).extracting(Pub::getId).contains(pub.getId());
    }

    @Test
    void 한국어_소개와_설명이_없고_영문도_없는_행은_그_사실만으로는_조회되지_않는다() {
        // intro, description은 선택 입력이라 비어 있을 수 있다.
        Pub pub = new Pub(college(), "컴퓨터공학과", "주점 이름", null, null, null);
        pub.applyTranslation("Pub Name", null, null, "Computer Science");
        entityManager.persistAndFlush(pub);

        List<Pub> result = pubRepository.findNeedingTranslation(PageRequest.of(0, 50));

        assertThat(result).extracting(Pub::getId).doesNotContain(pub.getId());
    }

    @Test
    void 결과는_id_오름차순으로_결정적으로_정렬된다() {
        College college = college();
        Pub third = new Pub(college, "학과3", "세번째", null, null, null);
        Pub first = new Pub(college, "학과1", "첫번째", null, null, null);
        Pub second = new Pub(college, "학과2", "두번째", null, null, null);
        entityManager.persistAndFlush(third);
        entityManager.persistAndFlush(first);
        entityManager.persistAndFlush(second);

        List<Pub> result = pubRepository.findNeedingTranslation(PageRequest.of(0, 50));

        assertThat(result).extracting(Pub::getId).isSorted();
    }
}
