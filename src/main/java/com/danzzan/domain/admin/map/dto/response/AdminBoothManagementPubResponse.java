package com.danzzan.domain.admin.map.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AdminBoothManagementPubResponse {
    private Long id;
    private String type;
    private String name;
    private String intro;
    private String description;
    private String collegeName;
    private String department;
    private String instagram;
    private boolean operationInfoExists;
}
