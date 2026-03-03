package com.danzzan.festival.domain.notice.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateEmergencyRequest {

    private String message;
    private Boolean isActive;
}
