package com.danzzan.domain.boothmap.model.dto;

import com.danzzan.domain.boothmap.model.entity.BoothOperationStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BoothSummaryResponse {
    private Long boothId;
    private String name;
    private String description;
    private String imageUrl;
    private String thumbnailUrl;
    private BoothOperationStatus operationStatus;
    private String startTime;
    private String endTime;
}
