package com.danzzan.domain.festival.theme.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import java.util.List;

/** A reviewed theme, ticket backgrounds, and an optional staged home banner, published in one transaction. */
public record ThemeBundleRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @Valid ThemeData themeData,
        @NotBlank @Size(max = 2048) @Pattern(regexp = "https?://[^\\s]+") String sourcePosterUrl,
        @NotNull @Size(min = 1, max = 8) List<@NotBlank @Pattern(regexp = "#[0-9A-Fa-f]{6}") String> sourceColors,
        @NotBlank @Pattern(regexp = "#[0-9A-Fa-f]{6}") String selectedBaseColor,
        @NotBlank @Size(max = 2048) String ticketCardBackgroundImageUrl,
        @NotBlank @Size(max = 2048) String ticketingBackgroundImageUrl,
        @Valid HomeBanner homeBanner
) {
    public record HomeBanner(
            @NotBlank @Size(max = 2048) String imageUrl,
            @Positive Long expectedFirstImageId,
            @Size(max = 2048) String expectedFirstImageUrl
    ) {}

    public ThemeBundleRequest(String name, ThemeData themeData, String sourcePosterUrl, List<String> sourceColors,
                              String selectedBaseColor, String ticketCardBackgroundImageUrl, String ticketingBackgroundImageUrl) {
        this(name, themeData, sourcePosterUrl, sourceColors, selectedBaseColor,
                ticketCardBackgroundImageUrl, ticketingBackgroundImageUrl, null);
    }
}
