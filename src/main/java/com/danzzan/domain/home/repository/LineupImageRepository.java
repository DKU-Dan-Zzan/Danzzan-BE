package com.danzzan.domain.home.repository;

import com.danzzan.domain.home.model.entity.LineupImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LineupImageRepository extends JpaRepository<LineupImage, Long> {
    List<LineupImage> findAllByOrderByDisplayOrderAsc();
}