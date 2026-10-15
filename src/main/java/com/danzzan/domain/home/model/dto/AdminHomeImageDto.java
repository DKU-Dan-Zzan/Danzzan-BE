package com.danzzan.domain.home.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.danzzan.infra.image.json.ImageUrlSerializer;
import com.danzzan.domain.home.model.entity.HomeImage;

public record AdminHomeImageDto(Long id, @JsonSerialize(using = ImageUrlSerializer.class) String imageUrl, int displayOrder) {
    public static AdminHomeImageDto from(HomeImage image) {
        return new AdminHomeImageDto(image.getId(), image.getImageUrl(), image.getDisplayOrder());
    }
}
