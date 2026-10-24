package com.danzzan.domain.festival.theme.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.danzzan.infra.image.json.ImageReferenceDeserializer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public record ThemeUpsertRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @Valid ThemeData themeData,
        @Size(max = 2048) @Pattern(regexp = "(?:https?://|azure:images/)[^\\s]+") @JsonDeserialize(using = ImageReferenceDeserializer.class) String sourcePosterUrl,
        @Pattern(regexp = "#[0-9A-Fa-f]{6}") String selectedBaseColor,
        @Size(max = 8) List<@NotBlank @Pattern(regexp = "#[0-9A-Fa-f]{6}") String> sourceColors
) {
    public ThemeUpsertRequest(String name, ThemeData themeData) {
        this(name, themeData, null, null, null);
    }
}
