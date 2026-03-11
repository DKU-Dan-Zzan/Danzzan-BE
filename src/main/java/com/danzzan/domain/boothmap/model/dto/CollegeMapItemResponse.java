package com.danzzan.domain.boothmap.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CollegeMapItemResponse {
    private Long collegeId;
    private String name;
    private Double locationX;
    private Double locationY;
}