package com.danzzan.domain.boothmap.repository;

import com.danzzan.domain.boothmap.model.entity.Pub;
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

    List<Pub> findTop50ByNameEnIsNullOrIntroEnIsNullOrDescriptionEnIsNullOrDepartmentEnIsNull();
}
