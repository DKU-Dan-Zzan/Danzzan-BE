package com.danzzan.domain.admin.map.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.danzzan.infra.image.json.ImageUrlSerializer;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.danzzan.domain.boothmap.model.entity.PubImage;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class AdminPubImageResponse {
    private Long id;
    @JsonSerialize(using = ImageUrlSerializer.class)
    private String imageUrl;
    @JsonProperty("isMain")
    private boolean isMain;
    private LocalDateTime createdAt;

    public static AdminPubImageResponse from(PubImage pubImage) {
        return new AdminPubImageResponse(
                pubImage.getId(),
                pubImage.getImageUrl(),
                pubImage.isMain(),
                pubImage.getCreatedAt()
        );
    }
}
