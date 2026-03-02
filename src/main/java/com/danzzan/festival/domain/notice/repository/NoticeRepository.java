package com.danzzan.festival.domain.notice.repository;

import com.danzzan.festival.domain.notice.entity.Notice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    Page<Notice> findByTitleContainingAndIsActiveTrue(String keyword, Pageable pageable);

    Page<Notice> findByIsActiveTrue(Pageable pageable);

    java.util.List<Notice> findByIsEmergencyTrue();
}
