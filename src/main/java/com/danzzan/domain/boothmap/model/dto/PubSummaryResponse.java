package com.danzzan.domain.boothmap.model.dto;

import com.danzzan.domain.boothmap.model.entity.BoothType;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PubSummaryResponse {
    private Long pubId;
    private String name;
    private String intro;
    private String department;
    private String collegeName;
    private String mainImageUrl;
}