package com.danzzan.domain.admin.map.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class AdminBoothManagementPubResponse {
    private Long id;
    private String type;
    private Long collegeId;
    private String name;
    private String intro;
    private String description;
    private String collegeName;
    private String department;
    private String instagram;
    private boolean operationInfoExists;
    private List<Long> displayOperationIds;
    private String nameEn;
    private String introEn;
    private String descriptionEn;
    private String departmentEn;
    private boolean enIsManual;
}
