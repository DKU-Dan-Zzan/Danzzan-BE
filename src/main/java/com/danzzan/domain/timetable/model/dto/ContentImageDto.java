package com.danzzan.domain.timetable.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.danzzan.infra.image.json.ImageUrlSerializer;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ContentImageDto {
    private Long id;
    private String name;
    @JsonSerialize(using = ImageUrlSerializer.class)
    private String previewImageUrl;
    @JsonSerialize(using = ImageUrlSerializer.class)
    private String detailImageUrl;
}