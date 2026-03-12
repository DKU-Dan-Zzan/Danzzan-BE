package com.danzzan.domain.boothmap.repository;

import com.danzzan.domain.boothmap.model.entity.Pub;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PubRepository extends JpaRepository<Pub, Long> {
}