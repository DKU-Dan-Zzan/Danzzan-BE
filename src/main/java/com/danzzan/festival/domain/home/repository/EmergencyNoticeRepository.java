package com.danzzan.festival.domain.home.repository;

import com.danzzan.festival.domain.home.model.entity.EmergencyNotice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmergencyNoticeRepository extends JpaRepository<EmergencyNotice, Integer> {
    // 최신 1건만 조회
    Optional<EmergencyNotice> findTopByOrderByCreatedAtDesc();
}