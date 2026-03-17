package com.danzzan.domain.notice.dto.response;

import com.danzzan.infra.s3.S3PresignedPutResult;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor
public class NoticeImagePresignResponse {
    private String uploadUrl;
    private String imageUrl;
    private String key;
    private Instant expiresAt;
    private String method = "PUT";

    public static NoticeImagePresignResponse from(S3PresignedPutResult result) {
        NoticeImagePresignResponse res = new NoticeImagePresignResponse();
        res.uploadUrl = result.uploadUrl();
        res.imageUrl = result.publicUrl();
        res.key = result.key();
        res.expiresAt = result.expiresAt();
        return res;
    }
}

