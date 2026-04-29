package com.danzzan.domain.admin.map.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class AdminBoothManagementResponse {
    private List<AdminBoothManagementBoothResponse> booths;
    private List<AdminBoothManagementPubResponse> pubs;
    private List<AdminPubOperationResponse> pubOperations;
}
