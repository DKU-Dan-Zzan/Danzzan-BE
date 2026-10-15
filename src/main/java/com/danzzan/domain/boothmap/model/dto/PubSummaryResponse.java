package com.danzzan.domain.boothmap.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.danzzan.infra.image.json.ImageUrlSerializer;
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
    @JsonSerialize(using = ImageUrlSerializer.class)
    private String mainImageUrl;
    @JsonSerialize(using = ImageUrlSerializer.class)
    private String thumbnailUrl;
    private String startTime;
    private String endTime;
}
