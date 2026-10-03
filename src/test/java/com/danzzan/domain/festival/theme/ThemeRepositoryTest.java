package com.danzzan.domain.festival.theme;

import com.danzzan.domain.festival.entity.FestivalSetting;
import com.danzzan.domain.festival.repository.FestivalSettingRepository;
import com.danzzan.domain.festival.theme.dto.ThemeData;
import com.danzzan.domain.festival.theme.entity.Theme;
import com.danzzan.domain.festival.theme.repository.ThemeRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
class ThemeRepositoryTest {
    @Autowired FestivalSettingRepository festivalSettingRepository;
    @Autowired ThemeRepository themeRepository;
    @Autowired EntityManager entityManager;

    @Test
    void persistsTypedThemeDataAsJson() {
        FestivalSetting setting = festivalSettingRepository.save(FestivalSetting.create("학교", "축제",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), false));
        ThemeData data = new ThemeData(
                new ThemeData.Brand("#FF642D", "#F5E3A3", "#FF642D", "#E13600"),
                new ThemeData.Status("#2E7D32", "#B26A00", "#B3261E", "#E3776A"),
                new ThemeData.Category("#FF642D", "#EC4899", "#10B981", "#F6CA3B", "#8B5CF6", "#3B82F6")
        );
        Theme theme = themeRepository.saveAndFlush(Theme.create(setting, "Orange", data));
        entityManager.clear();

        Theme loaded = themeRepository.findByIdAndFestivalSetting_Id(theme.getId(), setting.getId()).orElseThrow();
        assertEquals(data, loaded.getThemeData());
        assertNotNull(loaded.getCreatedAt());
        assertNotNull(loaded.getUpdatedAt());
    }
}
