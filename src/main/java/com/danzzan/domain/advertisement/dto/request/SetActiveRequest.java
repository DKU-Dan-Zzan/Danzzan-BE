package com.danzzan.domain.advertisement.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SetActiveRequest {

    private Boolean isActive = true;
}
