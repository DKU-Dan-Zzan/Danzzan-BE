package com.danzzan.domain.admin.map.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateActiveOperationDateRequest {
    private String operationDate;
}