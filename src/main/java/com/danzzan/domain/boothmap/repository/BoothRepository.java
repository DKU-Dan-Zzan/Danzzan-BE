package com.danzzan.domain.boothmap.repository;

import com.danzzan.domain.boothmap.model.entity.Booth;
import org.springframework.data.domain.Pageable;
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

    /**
     * 번역이 필요한 부스만 조회한다. 한국어 원문이 실제로 존재하는데(공백이 아닌데)
     * 영문이 비어 있는 행만 대상으로 한다 - 원문 자체가 없는 행(예: FOOD_TRUCK이 아닌
     * 부스는 description이 처음부터 null)은 영원히 채울 수 없으므로 제외해야
     * 스케줄러가 진짜 보정 대상 50건을 매 실행마다 계속 처리할 수 있다.
     */
    @Query("""
        select b from Booth b
        where (b.nameEn is null and b.name is not null and b.name <> '')
           or (b.descriptionEn is null and b.description is not null and b.description <> '')
        order by b.id
    """)
    List<Booth> findNeedingTranslation(Pageable pageable);
}