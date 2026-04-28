package com.danzzan.domain.admin.map.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AdminPubOperationResponse {
    private Long id;
    private String operationDate;
    private String startTime;
    private String endTime;
}
