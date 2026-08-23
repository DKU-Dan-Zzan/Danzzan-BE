package com.danzzan.domain.boothmap.repository;

import com.danzzan.domain.boothmap.model.entity.Pub;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PubRepository extends JpaRepository<Pub, Long> {
    @Query("""
    SELECT DISTINCT p FROM Pub p
    JOIN FETCH p.college
    LEFT JOIN FETCH p.displayDays displayDay
    LEFT JOIN FETCH displayDay.pubOperation
    """)
    List<Pub> findAllWithCollegeAndDisplayDays();

    @Query("""
    SELECT p FROM Pub p
    JOIN FETCH p.college
    WHERE p.id = :pubId
    """)
    Optional<Pub> findByIdWithCollege(Long pubId);

    @Query("""
    SELECT DISTINCT p
    FROM Pub p
    JOIN FETCH p.college
    LEFT JOIN FETCH p.images
    JOIN p.displayDays displayDay
    WHERE displayDay.pubOperation.id = :pubOperationId
    """)
    List<Pub> findAllVisibleByPubOperationIdWithCollegeAndImages(Long pubOperationId);

    @Query("""
    SELECT DISTINCT p
    FROM Pub p
    JOIN FETCH p.college
    JOIN p.displayDays displayDay
    WHERE p.id = :pubId
    AND displayDay.pubOperation.id = :pubOperationId
    """)
    Optional<Pub> findVisibleByIdAndPubOperationIdWithCollege(Long pubId, Long pubOperationId);

    /**
     * 번역이 필요한 주점만 조회한다. 한국어 원문이 실제로 존재하는데(공백이 아닌데)
     * 영문이 비어 있는 행만 대상으로 한다 - 원문 자체가 없는 필드(intro/description은
     * 선택 입력이라 null일 수 있다)까지 포함하면 영원히 채울 수 없는 행이 50건 창을
     * 영구히 차지해 스케줄러가 멈춘다.
     */
    @Query("""
        select p from Pub p
        where (p.nameEn is null and p.name is not null and p.name <> '')
           or (p.introEn is null and p.intro is not null and p.intro <> '')
           or (p.descriptionEn is null and p.description is not null and p.description <> '')
           or (p.departmentEn is null and p.department is not null and p.department <> '')
        order by p.id
    """)
    List<Pub> findNeedingTranslation(Pageable pageable);
}
