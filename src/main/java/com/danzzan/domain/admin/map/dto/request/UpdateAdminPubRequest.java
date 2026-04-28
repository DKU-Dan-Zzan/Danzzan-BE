package com.danzzan.domain.admin.map.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdateAdminPubRequest {
    private String intro;
    private String description;
    private String instagram;
}
