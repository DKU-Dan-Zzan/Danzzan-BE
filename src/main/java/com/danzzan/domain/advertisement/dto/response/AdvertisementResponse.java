package com.danzzan.domain.advertisement.dto.response;

import com.danzzan.domain.advertisement.model.entity.Advertisement;
import com.danzzan.domain.advertisement.model.entity.AdvertisementPlacement;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class AdvertisementResponse {

    private Long id;
    private String title;
    private String imageUrl;
    private String linkUrl;
    private AdvertisementPlacement placement;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean isActive;
    private Integer priority;
    private LocalDateTime createdAt;

    public static AdvertisementResponse from(Advertisement ad) {
        AdvertisementResponse res = new AdvertisementResponse();
        res.id = ad.getId();
        res.title = ad.getTitle();
        res.imageUrl = ad.getImageUrl();
        res.linkUrl = ad.getLinkUrl();
        res.placement = ad.getPlacement();
        res.startDate = ad.getStartDate();
        res.endDate = ad.getEndDate();
        res.isActive = ad.getIsActive();
        res.priority = ad.getPriority();
        res.createdAt = ad.getCreatedAt();
        return res;
    }
}
