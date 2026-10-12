package com.danzzan.domain.festival.theme.repository;

import com.danzzan.domain.festival.theme.entity.Theme;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ThemeRepository extends JpaRepository<Theme, Long> {
    List<Theme> findByFestivalSetting_IdOrderByIdDesc(Long festivalSettingId);

    Optional<Theme> findByIdAndFestivalSetting_Id(Long id, Long festivalSettingId);
}
