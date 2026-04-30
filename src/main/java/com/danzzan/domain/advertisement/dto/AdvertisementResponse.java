package com.danzzan.domain.advertisement.dto;

import com.danzzan.domain.advertisement.Advertisement;
import com.danzzan.domain.advertisement.AdvertisementPlacement;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class AdvertisementResponse {

    private Long id;
    private String title;
    private String imageUrl;
    private String linkUrl;
    private AdvertisementPlacement placement;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AdvertisementResponse from(Advertisement ad) {
        AdvertisementResponse res = new AdvertisementResponse();
        res.id = ad.getId();
        res.title = ad.getTitle();
        res.imageUrl = ad.getImageUrl();
        res.linkUrl = ad.getLinkUrl();
        res.placement = ad.getPlacement();
        res.isActive = ad.getIsActive();
        res.createdAt = ad.getCreatedAt();
        res.updatedAt = ad.getUpdatedAt();
        return res;
    }
}
