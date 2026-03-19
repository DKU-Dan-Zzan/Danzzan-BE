package com.danzzan.domain.admin.map.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class AdminMapResponse {
    private String activeOperationDate;
    private List<AdminMapCollegeResponse> colleges;
    private List<AdminMapBoothResponse> booths;
}