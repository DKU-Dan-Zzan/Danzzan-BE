package com.danzzan.domain.boothmap.repository;

import com.danzzan.domain.boothmap.model.entity.PubOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PubOperationRepository extends JpaRepository<PubOperation, Long> {
    Optional<PubOperation> findByOperationDate(LocalDate operationDate);
    Optional<PubOperation> findFirstByOrderByOperationDateAsc();
    List<PubOperation> findAllByOrderByOperationDateAsc();
    List<PubOperation> findAllByOperationDateIn(Collection<LocalDate> operationDates);
}
