package com.danzzan.domain.advertisement.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SetAdvertisementActiveRequest {

    @NotNull(message = "active 값을 입력해 주세요.")
    private Boolean active;
}
