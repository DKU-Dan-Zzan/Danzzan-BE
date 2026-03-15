package com.danzzan.domain.advertisement.dto.response;

import com.danzzan.infra.s3.S3PresignedPutResult;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor
public class AdImagePresignResponse {

    private String presignedUrl;
    private String imageUrl;
    private String key;
    private Instant expiresAt;
    private String method = "PUT";

    public static AdImagePresignResponse from(S3PresignedPutResult result) {
        AdImagePresignResponse res = new AdImagePresignResponse();
        res.presignedUrl = result.uploadUrl();
        res.imageUrl = result.publicUrl();
        res.key = result.key();
        res.expiresAt = result.expiresAt();
        return res;
    }
}

