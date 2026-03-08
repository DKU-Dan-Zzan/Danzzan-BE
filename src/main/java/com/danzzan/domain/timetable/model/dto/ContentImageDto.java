package com.danzzan.domain.timetable.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ContentImageDto {
    private Long id;
    private String name;
    private String previewImageUrl;
    private String detailImageUrl;
}