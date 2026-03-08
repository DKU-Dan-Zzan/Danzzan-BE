package com.danzzan.domain.timetable.repository;

import com.danzzan.domain.timetable.model.entity.ContentImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContentImageRepository extends JpaRepository<ContentImage, Long> {
    // display_order 기준 전체 조회
    List<ContentImage> findAllByOrderByDisplayOrderAsc();
}