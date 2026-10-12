package com.danzzan.domain.home.model.dto;

import jakarta.validation.constraints.*;

public record HomeBannerPreviewRequest(
        @NotBlank @Size(max = 2048) @Pattern(regexp = "https?://[^\\s]+") String sourcePosterUrl,
        @NotNull @Pattern(regexp = "contain|cover") String layout,
        @DecimalMin("0.0") @DecimalMax("1.0") Double focusX,
        @DecimalMin("0.0") @DecimalMax("1.0") Double focusY
) {}
