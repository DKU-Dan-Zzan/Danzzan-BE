package com.danzzan.domain.boothmap.repository;

import com.danzzan.domain.boothmap.model.entity.Pub;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.List;

public interface PubRepository extends JpaRepository<Pub, Long> {
    @Query("""
    SELECT p FROM Pub p
    JOIN FETCH p.college
    """)
    List<Pub> findAllWithCollege();

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
    LEFT JOIN FETCH p.images img
        WITH img.isMain = true
    """)
    List<Pub> findAllWithCollegeAndMainImage();
}