package com.danzzan.domain.festival.repository;

import com.danzzan.domain.festival.entity.FestivalSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FestivalSettingRepository extends JpaRepository<FestivalSetting, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FestivalSetting f where f.id = :id")
    Optional<FestivalSetting> findByIdForUpdate(@Param("id") Long id);
}
