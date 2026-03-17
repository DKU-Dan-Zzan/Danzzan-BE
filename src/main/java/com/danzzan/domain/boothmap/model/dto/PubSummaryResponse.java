package com.danzzan.domain.boothmap.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PubSummaryResponse {
    private Long pubId;
    private String name;
    private String intro;
    private String department;
    private Long collegeId;
    private String collegeName;
    private String mainImageUrl;
    private String startTime;
    private String endTime;
}