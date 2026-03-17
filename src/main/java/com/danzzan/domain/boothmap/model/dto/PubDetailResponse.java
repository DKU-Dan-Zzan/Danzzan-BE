package com.danzzan.domain.boothmap.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class PubDetailResponse {
    private Long pubId;
    private String name;
    private String intro;
    private String description;
    private String department;
    private String collegeName;
    private String instagram;
    private List<String> imageUrls;
    private String startTime;
    private String endTime;
}