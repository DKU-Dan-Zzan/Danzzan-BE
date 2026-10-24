package com.danzzan.domain.home.model.dto;

import com.danzzan.infra.image.json.ImageReferenceDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.*;

public record HomeBannerPreviewRequest(
        @NotBlank @Size(max = 2048) @Pattern(regexp = "(?:https?://|azure:images/)[^\\s]+")
        @JsonDeserialize(using = ImageReferenceDeserializer.class) String sourcePosterUrl,
        @NotNull @Pattern(regexp = "contain|cover") String layout,
        @DecimalMin("0.0") @DecimalMax("1.0") Double focusX,
        @DecimalMin("0.0") @DecimalMax("1.0") Double focusY
) {}
