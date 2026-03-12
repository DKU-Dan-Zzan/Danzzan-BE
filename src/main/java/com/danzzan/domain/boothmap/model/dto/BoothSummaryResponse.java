package com.danzzan.domain.boothmap.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BoothSummaryResponse {
    private Long boothId;
    private String name;
    private String description;
    private String imageUrl;
}