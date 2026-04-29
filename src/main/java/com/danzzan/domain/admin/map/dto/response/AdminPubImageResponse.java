package com.danzzan.domain.admin.map.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.danzzan.domain.boothmap.model.entity.PubImage;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class AdminPubImageResponse {
    private Long id;
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
