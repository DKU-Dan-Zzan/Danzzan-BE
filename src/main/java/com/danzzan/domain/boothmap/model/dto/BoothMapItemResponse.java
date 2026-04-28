package com.danzzan.domain.boothmap.model.dto;

import com.danzzan.domain.boothmap.model.entity.BoothType;
import com.danzzan.domain.boothmap.model.entity.BoothSubType;
import com.danzzan.domain.boothmap.model.entity.BoothOperationStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BoothMapItemResponse {
    private Long boothId;
    private String name;
    private BoothType type;
    private BoothSubType subType;
    private Double locationX;
    private Double locationY;
    private BoothOperationStatus operationStatus;
    private String startTime;
    private String endTime;
}
