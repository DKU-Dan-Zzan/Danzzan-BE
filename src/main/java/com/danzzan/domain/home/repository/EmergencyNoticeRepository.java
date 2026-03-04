package com.danzzan.domain.home.repository;

import com.danzzan.domain.home.model.entity.EmergencyNotice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository("homeEmergencyNoticeRepository")
public interface EmergencyNoticeRepository extends JpaRepository<EmergencyNotice, Integer> {
    // 최신 1건만 조회
    Optional<EmergencyNotice> findTopByOrderByCreatedAtDesc();
}
