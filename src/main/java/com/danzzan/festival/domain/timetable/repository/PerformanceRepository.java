package com.danzzan.festival.domain.timetable.repository;

import com.danzzan.festival.domain.timetable.model.entity.Performance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.time.LocalDate;

public interface PerformanceRepository extends JpaRepository<Performance, Integer> {
    @Query("""
        select p from Performance p
        join fetch p.artist
        where p.performanceDate = :date
        order by p.startTime asc
    """)
    List<Performance> findByDateWithArtist(@Param("date") LocalDate date);
}