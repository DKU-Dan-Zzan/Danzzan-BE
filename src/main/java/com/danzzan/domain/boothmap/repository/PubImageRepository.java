package com.danzzan.domain.boothmap.repository;

import com.danzzan.domain.boothmap.model.entity.PubImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface PubImageRepository extends JpaRepository<PubImage, Long> {
    Optional<PubImage> findByPubIdAndIsMainTrue(Long pubId);

    List<PubImage> findByPubId(Long pubId);
}