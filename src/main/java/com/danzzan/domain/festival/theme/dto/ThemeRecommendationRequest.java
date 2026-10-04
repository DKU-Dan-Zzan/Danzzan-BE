package com.danzzan.domain.festival.theme.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ThemeRecommendationRequest(
        @NotEmpty @Size(max = 8) List<@NotBlank @Pattern(regexp = "#[0-9A-Fa-f]{6}") String> sourceColors,
        @NotBlank @Pattern(regexp = "#[0-9A-Fa-f]{6}") String selectedBaseColor
) {}
