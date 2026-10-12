package com.danzzan.infra.image;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ImageReadUrlRequest(@NotBlank @Size(max = 8192) String imageUrl) {
}
