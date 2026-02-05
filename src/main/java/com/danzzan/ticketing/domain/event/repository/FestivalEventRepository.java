package com.danzzan.ticketing.domain.event.repository;

import com.danzzan.ticketing.domain.event.model.entity.FestivalEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FestivalEventRepository extends JpaRepository<FestivalEvent, Long> {
}
