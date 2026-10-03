package com.danzzan.domain.festival.theme.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ThemeUpsertRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @Valid ThemeData themeData
) {}
