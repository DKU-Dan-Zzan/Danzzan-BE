package com.danzzan.domain.boothmap.repository;

import com.danzzan.domain.boothmap.model.entity.Booth;
import com.danzzan.domain.boothmap.model.entity.BoothType;
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
 * BoothRepository의 백필 대상 조회 쿼리를 실제 DB에 대해 검증한다.
 * findTop50ByNameEnIsNullOrDescriptionEnIsNull()은 한국어 원문이 애초에
 * null인 행(예: FOOD_TRUCK이 아닌 부스는 description이 정책상 항상 null)까지
 * 대상으로 잡아, 그런 행이 50건을 넘으면 스케줄러가 영원히 멈추는 버그가 있었다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:booth-repository;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class BoothRepositoryTest {

    @Autowired
    private BoothRepository boothRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void 한국어_이름이_있고_영문_이름이_비어있으면_보정_대상으로_조회된다() {
        Booth booth = new Booth("체험 부스", BoothType.EXPERIENCE, "부스 설명", null, null, null);
        entityManager.persistAndFlush(booth);

        List<Booth> result = boothRepository.findNeedingTranslation(PageRequest.of(0, 50));

        assertThat(result).extracting(Booth::getId).contains(booth.getId());
    }

    @Test
    void 한국어_설명이_없고_영문_설명도_없는_행은_그_사실만으로는_조회되지_않는다() {
        // FOOD_TRUCK이 아닌 부스는 정책상 description이 처음부터 null이다.
        // 이름은 이미 번역되어 있어 유일한 차이는 "설명이 둘 다 비어 있다"는 것뿐이다.
        Booth booth = new Booth("먹거리 트럭", BoothType.FOOD_TRUCK, null, null, null, null);
        booth.applyTranslation("Food Truck", null);
        entityManager.persistAndFlush(booth);

        List<Booth> result = boothRepository.findNeedingTranslation(PageRequest.of(0, 50));

        assertThat(result).extracting(Booth::getId).doesNotContain(booth.getId());
    }

    @Test
    void 결과는_id_오름차순으로_결정적으로_정렬된다() {
        Booth third = new Booth("세번째", BoothType.EXPERIENCE, null, null, null, null);
        Booth first = new Booth("첫번째", BoothType.EXPERIENCE, null, null, null, null);
        Booth second = new Booth("두번째", BoothType.EXPERIENCE, null, null, null, null);
        entityManager.persistAndFlush(third);
        entityManager.persistAndFlush(first);
        entityManager.persistAndFlush(second);

        List<Booth> result = boothRepository.findNeedingTranslation(PageRequest.of(0, 50));

        assertThat(result).extracting(Booth::getId).isSorted();
    }
}
