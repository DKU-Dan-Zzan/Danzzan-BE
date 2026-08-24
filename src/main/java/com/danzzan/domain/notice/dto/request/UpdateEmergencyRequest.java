package com.danzzan.domain.notice.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateEmergencyRequest {

    private String message;
    private Boolean isActive;

    /** 관리자가 직접 입력한 영문. 비워두면 자동 번역한다. */
    private String messageEn;
}
