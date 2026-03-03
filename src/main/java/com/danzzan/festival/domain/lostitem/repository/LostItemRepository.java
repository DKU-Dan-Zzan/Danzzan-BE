package com.danzzan.festival.domain.lostitem.repository;

import com.danzzan.festival.domain.lostitem.entity.LostItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LostItemRepository extends JpaRepository<LostItem, Long> {

    Page<LostItem> findByIsActiveTrue(Pageable pageable);

    Page<LostItem> findByIsActiveTrueAndIsClaimed(Boolean isClaimed, Pageable pageable);
}

