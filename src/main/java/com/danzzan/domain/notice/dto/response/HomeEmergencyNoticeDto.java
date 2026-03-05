package com.danzzan.domain.notice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class HomeEmergencyNoticeDto {
    private Integer id;
    private String content;
    private String createdAt;
    private String updatedAt;
    private Boolean isActive;
}
