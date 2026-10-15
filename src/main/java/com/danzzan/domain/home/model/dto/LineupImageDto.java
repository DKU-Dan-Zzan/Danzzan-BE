package com.danzzan.domain.home.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.danzzan.infra.image.json.ImageUrlSerializer;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LineupImageDto {
    private Long id;
    @JsonSerialize(using = ImageUrlSerializer.class)
    private String imageUrl;
}