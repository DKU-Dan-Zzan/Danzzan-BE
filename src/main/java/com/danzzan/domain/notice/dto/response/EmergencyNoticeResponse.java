package com.danzzan.domain.notice.dto.response;

import com.danzzan.domain.notice.entity.EmergencyNotice;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class EmergencyNoticeResponse {

    private Long id;
    private String message;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static EmergencyNoticeResponse from(EmergencyNotice entity) {
        EmergencyNoticeResponse res = new EmergencyNoticeResponse();
        res.id = entity.getId();
        res.message = entity.getMessage();
        res.isActive = entity.getIsActive();
        res.createdAt = entity.getCreatedAt();
        res.updatedAt = entity.getUpdatedAt();
        return res;
    }
}
