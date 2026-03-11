package com.danzzan.domain.boothmap.model.dto;

import com.danzzan.domain.boothmap.model.entity.BoothType;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BoothMapItemResponse {
    private Long boothId;
    private String name;
    private BoothType type;
    private Double locationX;
    private Double locationY;
}