package com.danzzan.domain.festival.repository;

import com.danzzan.domain.festival.entity.FestivalSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FestivalSettingRepository extends JpaRepository<FestivalSetting, Long> {
}
