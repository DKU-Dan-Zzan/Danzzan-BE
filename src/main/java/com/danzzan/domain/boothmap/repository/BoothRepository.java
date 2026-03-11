package com.danzzan.domain.boothmap.repository;

import com.danzzan.domain.boothmap.model.entity.Booth;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BoothRepository extends JpaRepository<Booth, Long> {
}