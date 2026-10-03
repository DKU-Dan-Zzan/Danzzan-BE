package com.danzzan.domain.festival.theme.dto;

import java.time.LocalDateTime;

/** Public runtime response intentionally omits provenance. */
public record PublicThemeResponse(
        Long id, Long festivalSettingId, String name, ThemeData themeData,
        LocalDateTime createdAt, LocalDateTime updatedAt, boolean active
) {
    public static PublicThemeResponse from(ThemeResponse theme) {
        return new PublicThemeResponse(theme.id(), theme.festivalSettingId(), theme.name(), theme.themeData(),
                theme.createdAt(), theme.updatedAt(), theme.active());
    }
}
