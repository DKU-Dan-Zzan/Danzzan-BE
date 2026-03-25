package com.danzzan.domain.advertisement.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SetAdvertisementActiveRequest {

    /**
     * 프론트는 {@code JSON.stringify({ isActive })} 형태로 전송.
     * 예전 {@code active} 키도 호환합니다.
     */
    @NotNull(message = "isActive 값을 입력해 주세요.")
    @JsonAlias("active")
    private Boolean isActive;
}
