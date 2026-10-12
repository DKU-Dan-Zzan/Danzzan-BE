package com.danzzan.domain.home.repository;

import com.danzzan.domain.home.model.entity.HomeImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

import java.util.List;

public interface HomeImageRepository extends JpaRepository<HomeImage, Long> {
    // display_order 기준 전체 조회
    @Query("select h from HomeImage h where h.festivalSettingId = :festivalSettingId order by h.displayOrder asc, h.id asc")
    List<HomeImage> findAllByFestivalSettingIdOrderByDisplayOrderAsc(@Param("festivalSettingId") Long festivalSettingId);
    Optional<HomeImage> findByIdAndFestivalSettingId(Long id, Long festivalSettingId);
}
