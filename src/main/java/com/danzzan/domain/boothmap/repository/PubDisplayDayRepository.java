package com.danzzan.domain.boothmap.repository;

import com.danzzan.domain.boothmap.model.entity.PubDisplayDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PubDisplayDayRepository extends JpaRepository<PubDisplayDay, Long> {
    List<PubDisplayDay> findByPubId(Long pubId);
    List<PubDisplayDay> findByPubOperationId(Long pubOperationId);
    void deleteByPubId(Long pubId);
}
