package com.danzzan.domain.festival.theme.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.danzzan.infra.image.json.ImageUrlSerializer;
import com.danzzan.domain.festival.theme.entity.Theme;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.List;

public record ThemeResponse(
        Long id,
        Long festivalSettingId,
        String name,
        ThemeData themeData,
        @JsonSerialize(using = ImageUrlSerializer.class) String sourcePosterUrl,
        String selectedBaseColor,
        List<String> sourceColors,
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
                theme.getSourcePosterUrl(),
                theme.getSelectedBaseColor(),
                theme.getSourceColors(),
                theme.getCreatedAt(),
                theme.getUpdatedAt(),
                Objects.equals(theme.getId(), activeThemeId)
        );
    }
}
