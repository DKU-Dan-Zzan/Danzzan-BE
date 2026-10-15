package com.danzzan.domain.boothmap.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.danzzan.infra.image.json.ImageUrlSerializer;
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
    @JsonSerialize(contentUsing = ImageUrlSerializer.class)
    private List<String> imageUrls;
    @JsonSerialize(contentUsing = ImageUrlSerializer.class)
    private List<String> thumbnailImageUrls;
    private String startTime;
    private String endTime;
}
