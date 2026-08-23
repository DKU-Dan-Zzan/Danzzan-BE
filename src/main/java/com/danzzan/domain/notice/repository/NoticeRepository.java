package com.danzzan.domain.notice.repository;

import com.danzzan.domain.notice.entity.Notice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    Page<Notice> findByTitleContainingAndIsActiveTrue(String keyword, Pageable pageable);

    Page<Notice> findByIsActiveTrue(Pageable pageable);

    @Query("""
            SELECT n FROM Notice n
            WHERE n.isActive = true
              AND (:category IS NULL OR n.category = :category)
              AND (
                :keyword IS NULL
                OR LOWER(n.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(n.content) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            ORDER BY COALESCE(n.isPinned, false) DESC, COALESCE(n.displayOrder, 0) ASC, n.createdAt DESC
            """)
    Page<Notice> searchActive(
            @Param("keyword") String keyword,
            @Param("category") String category,
            Pageable pageable
    );

    @Query("""
            SELECT n FROM Notice n
            WHERE (:isActive IS NULL OR n.isActive = :isActive)
              AND (:category IS NULL OR n.category = :category)
              AND (
                :keyword IS NULL
                OR LOWER(n.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(n.content) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            ORDER BY COALESCE(n.isPinned, false) DESC, COALESCE(n.displayOrder, 0) ASC, n.createdAt DESC
            """)
    Page<Notice> searchByStatus(
            @Param("keyword") String keyword,
            @Param("category") String category,
            @Param("isActive") Boolean isActive,
            Pageable pageable
    );

    @Query("""
            SELECT n FROM Notice n
            WHERE n.id = :id
              AND n.isActive = true
            """)
    java.util.Optional<Notice> findActiveById(@Param("id") Long id);

    java.util.List<Notice> findByIsEmergencyTrue();

    List<Notice> findTop50ByTitleEnIsNullOrContentEnIsNull();
}
