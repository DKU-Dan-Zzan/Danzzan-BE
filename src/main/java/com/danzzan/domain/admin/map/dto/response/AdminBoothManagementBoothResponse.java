package com.danzzan.domain.admin.map.dto.response;

import com.danzzan.domain.boothmap.model.entity.BoothOperationStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AdminBoothManagementBoothResponse {
    private Long id;
    private String type;
    private String name;
    private String description;
    private boolean operationInfoExists;
    private BoothOperationStatus operationStatus;
    private String startTime;
    private String endTime;
}
