package com.danzzan.domain.boothmap.repository;

import com.danzzan.domain.boothmap.model.entity.BoothOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BoothOperationRepository extends JpaRepository<BoothOperation, Long> {
    @Query("""
        select bo
        from BoothOperation bo
        join fetch bo.booth b
        where bo.operationDate = :operationDate
    """)
    List<BoothOperation> findAllWithBoothByOperationDate(LocalDate operationDate);

    @Query("""
        select bo
        from BoothOperation bo
        join fetch bo.booth b
        where b.id = :boothId
          and bo.operationDate = :operationDate
    """)
    Optional<BoothOperation> findByBoothIdAndOperationDate(Long boothId, LocalDate operationDate);

    @Query("select bo from BoothOperation bo join fetch bo.booth")
    List<BoothOperation> findAllWithBooth();

    @Query("""
        select bo
        from BoothOperation bo
        where bo.booth.id in :boothIds
        order by bo.operationDate asc
    """)
    List<BoothOperation> findAllByBoothIdInOrderByOperationDateAsc(Collection<Long> boothIds);

    Optional<BoothOperation> findFirstByBoothIdOrderByOperationDateAsc(Long boothId);
}
