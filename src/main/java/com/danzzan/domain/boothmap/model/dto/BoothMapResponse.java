package com.danzzan.domain.boothmap.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class BoothMapResponse {
    private List<CollegeMapItemResponse> colleges;
    private List<BoothMapItemResponse> booths;
}