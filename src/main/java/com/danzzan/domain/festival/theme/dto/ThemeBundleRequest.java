package com.danzzan.domain.festival.theme.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** A reviewed theme and two staged uploads, published in one transaction. */
public record ThemeBundleRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @Valid ThemeData themeData,
        @NotBlank @Size(max = 2048) @Pattern(regexp = "https?://[^\\s]+") String sourcePosterUrl,
        @NotNull @Size(min = 1, max = 8) List<@NotBlank @Pattern(regexp = "#[0-9A-Fa-f]{6}") String> sourceColors,
        @NotBlank @Pattern(regexp = "#[0-9A-Fa-f]{6}") String selectedBaseColor,
        @NotBlank @Size(max = 2048) String ticketCardBackgroundImageUrl,
        @NotBlank @Size(max = 2048) String ticketingBackgroundImageUrl
) {}
