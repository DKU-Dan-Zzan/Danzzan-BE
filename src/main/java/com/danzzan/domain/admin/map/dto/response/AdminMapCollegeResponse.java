package com.danzzan.domain.admin.map.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AdminMapCollegeResponse {
    private Long id;
    private String name;
    private Double locationX;
    private Double locationY;
}