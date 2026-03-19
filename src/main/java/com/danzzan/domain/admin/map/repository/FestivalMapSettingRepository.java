package com.danzzan.domain.admin.map.repository;

import com.danzzan.domain.admin.map.model.entity.FestivalMapSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FestivalMapSettingRepository extends JpaRepository<FestivalMapSetting, Long> {
}