package com.danzzan.domain.festival.theme.dto;

import com.danzzan.domain.festival.theme.entity.Theme;

import java.time.LocalDateTime;
import java.util.Objects;

public record ThemeResponse(
        Long id,
        Long festivalSettingId,
        String name,
        ThemeData themeData,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean active
) {
    public static ThemeResponse of(Theme theme, Long activeThemeId) {
        return new ThemeResponse(
                theme.getId(),
                theme.getFestivalSetting().getId(),
                theme.getName(),
                theme.getThemeData(),
                theme.getCreatedAt(),
                theme.getUpdatedAt(),
                Objects.equals(theme.getId(), activeThemeId)
        );
    }
}
