package com.danzzan.domain.boothmap.repository;

import com.danzzan.domain.boothmap.model.entity.College;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollegeRepository extends JpaRepository<College, Long> {
}