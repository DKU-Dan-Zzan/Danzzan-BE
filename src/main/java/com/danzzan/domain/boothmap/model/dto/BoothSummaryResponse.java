package com.danzzan.domain.boothmap.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.danzzan.infra.image.json.ImageUrlSerializer;
import com.danzzan.domain.boothmap.model.entity.BoothOperationStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BoothSummaryResponse {
    private Long boothId;
    private String name;
    private String description;
    @JsonSerialize(using = ImageUrlSerializer.class)
    private String imageUrl;
    @JsonSerialize(using = ImageUrlSerializer.class)
    private String thumbnailUrl;
    private BoothOperationStatus operationStatus;
    private String startTime;
    private String endTime;
}
