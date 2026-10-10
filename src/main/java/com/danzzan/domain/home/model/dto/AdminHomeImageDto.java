package com.danzzan.domain.home.model.dto;

import com.danzzan.domain.home.model.entity.HomeImage;

public record AdminHomeImageDto(Long id, String imageUrl, int displayOrder) {
    public static AdminHomeImageDto from(HomeImage image) {
        return new AdminHomeImageDto(image.getId(), image.getImageUrl(), image.getDisplayOrder());
    }
}
