package com.danzzan.domain.boothmap.repository;

import com.danzzan.domain.boothmap.model.entity.Booth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

import java.util.List;

public interface BoothRepository extends JpaRepository<Booth, Long> {
    @Query("""
        select distinct b
        from Booth b
        join BoothOperation bo on bo.booth.id = b.id
        where bo.operationDate = :operationDate
    """)
    List<Booth> findAllByOperationDate(@Param("operationDate") LocalDate operationDate);

    List<Booth> findTop50ByNameEnIsNullOrDescriptionEnIsNull();
}