package com.danzzan.domain.home.repository;

import com.danzzan.domain.home.model.entity.HomeImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HomeImageRepository extends JpaRepository<HomeImage, Long> {
    // display_order 기준 전체 조회
    List<HomeImage> findAllByOrderByDisplayOrderAsc();
}