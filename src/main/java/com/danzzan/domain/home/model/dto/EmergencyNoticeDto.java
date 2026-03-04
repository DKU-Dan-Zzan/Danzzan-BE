package com.danzzan.domain.home.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class EmergencyNoticeDto {
    private Integer id;
    private String content;
    private String createdAt;
}