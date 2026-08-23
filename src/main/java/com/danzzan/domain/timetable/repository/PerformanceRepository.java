package com.danzzan.domain.timetable.repository;

import com.danzzan.domain.timetable.model.entity.Performance;
import org.springframework.data.domain.Pageable;
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

    @Query("""
        select p from Performance p
        join fetch p.artist
        where p.id = :id
    """)
    java.util.Optional<Performance> findByIdWithArtist(@Param("id") Integer id);

    boolean existsByArtistId(Integer artistId);

    /**
     * 번역이 필요한 공연만 조회한다. 한국어 원문이 실제로 존재하는데(공백이 아닌데)
     * 영문이 비어 있는 행만 대상으로 한다 - stage는 선택 입력이라 null일 수 있으므로,
     * 원문 자체가 없는 행까지 포함하면 영원히 채울 수 없는 행이 50건 창을 영구히
     * 차지해 스케줄러가 멈춘다.
     */
    @Query("""
        select p from Performance p
        where p.stageEn is null and p.stage is not null and p.stage <> ''
        order by p.id
    """)
    List<Performance> findNeedingTranslation(Pageable pageable);
}