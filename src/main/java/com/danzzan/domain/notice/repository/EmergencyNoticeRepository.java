package com.danzzan.domain.notice.repository;

import com.danzzan.domain.notice.entity.EmergencyNotice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmergencyNoticeRepository extends JpaRepository<EmergencyNotice, Long> {

    Optional<EmergencyNotice> findFirstByOrderByIdAsc();
}
