package com.danzzan.domain.advertisement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdvertisementRepository extends JpaRepository<Advertisement, Long> {

    List<Advertisement> findByPlacementOrderByCreatedAtDesc(AdvertisementPlacement placement);

    List<Advertisement> findAllByDeletedAtIsNullOrderByCreatedAtDesc();

    List<Advertisement> findAllByIsActiveTrueAndDeletedAtIsNullOrderByCreatedAtDesc();

    Optional<Advertisement> findFirstByPlacementAndIsActiveTrueAndDeletedAtIsNullOrderByCreatedAtDesc(
            AdvertisementPlacement placement);

    Optional<Advertisement> findFirstByPlacementAndDeletedAtIsNullOrderByCreatedAtDesc(
            AdvertisementPlacement placement);
}
