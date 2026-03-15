package com.danzzan.domain.advertisement.repository;

import com.danzzan.domain.advertisement.model.entity.Advertisement;
import com.danzzan.domain.advertisement.model.entity.AdvertisementPlacement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface AdvertisementRepository extends JpaRepository<Advertisement, Long> {

    Page<Advertisement> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 노출 기간 내이고 활성화된, 특정 placement 광고를 priority 내림차순, createdAt 내림차순으로 조회.
     */
    @Query("""
            SELECT a FROM Advertisement a
            WHERE a.placement = :placement
              AND a.isActive = true
              AND a.startDate <= :date
              AND a.endDate >= :date
            ORDER BY a.priority DESC, a.createdAt DESC
            """)
    List<Advertisement> findActiveByPlacementAndDate(
            @Param("placement") AdvertisementPlacement placement,
            @Param("date") LocalDate date
    );

    /**
     * 노출 기간 내이고 활성화된 GLOBAL 광고 목록 (랜덤 선택용).
     */
    @Query("""
            SELECT a FROM Advertisement a
            WHERE a.placement = 'GLOBAL'
              AND a.isActive = true
              AND a.startDate <= :date
              AND a.endDate >= :date
            ORDER BY a.priority DESC, a.createdAt DESC
            """)
    List<Advertisement> findActiveGlobalByDate(@Param("date") LocalDate date);
}
