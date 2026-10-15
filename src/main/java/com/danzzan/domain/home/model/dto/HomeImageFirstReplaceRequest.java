package com.danzzan.domain.home.model.dto;

import com.danzzan.infra.image.json.ImageReferenceDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Publishes one already-staged home image without altering any later banner. */
public record HomeImageFirstReplaceRequest(
        @NotBlank @Size(max = 2048) @JsonDeserialize(using = ImageReferenceDeserializer.class) String imageUrl,
        @Positive Long expectedFirstImageId,
        @Size(max = 2048) @JsonDeserialize(using = ImageReferenceDeserializer.class) String expectedFirstImageUrl,
        @Size(max = 2048) @Pattern(regexp = "(?:https?://|azure:images/)[^\\s]+")
        @JsonDeserialize(using = ImageReferenceDeserializer.class) String sourcePosterUrl
) {}
