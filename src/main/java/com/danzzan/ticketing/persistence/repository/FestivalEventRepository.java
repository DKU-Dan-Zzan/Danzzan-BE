package com.danzzan.ticketing.persistence.repository;

import com.danzzan.ticketing.persistence.entity.FestivalEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FestivalEventRepository extends JpaRepository<FestivalEvent, Long> {
}
