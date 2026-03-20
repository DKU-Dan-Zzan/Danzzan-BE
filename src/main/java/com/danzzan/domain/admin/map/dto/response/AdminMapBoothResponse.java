package com.danzzan.domain.admin.map.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AdminMapBoothResponse {
    private Long id;
    private String name;
    private String type;
    private Double locationX;
    private Double locationY;
    private boolean placed;
}