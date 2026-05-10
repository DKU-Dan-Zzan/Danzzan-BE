package com.danzzan.domain.admin.map.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class UpdateAdminPubRequest {
    private String name;
    private String intro;
    private String description;
    private String instagram;
    @NotEmpty(message = "표시 일자는 최소 1개 이상 선택해야 합니다.")
    private List<@NotNull(message = "표시 일자 ID는 null일 수 없습니다.") Long> displayOperationIds;
}
