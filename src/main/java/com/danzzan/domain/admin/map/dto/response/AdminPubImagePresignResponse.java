package com.danzzan.domain.admin.map.dto.response;

import com.danzzan.infra.s3.S3PresignedPutResult;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Getter
@NoArgsConstructor
public class AdminPubImagePresignResponse {
    private String presignedUrl;
    private String fileUrl;
    private String key;
    private Instant expiresAt;
    private String method = "PUT";

    public static AdminPubImagePresignResponse from(S3PresignedPutResult result) {
        Objects.requireNonNull(result, "presign result must not be null");
        AdminPubImagePresignResponse response = new AdminPubImagePresignResponse();
        response.presignedUrl = Objects.requireNonNull(result.uploadUrl(), "presignedUrl must not be null");
        response.fileUrl = Objects.requireNonNull(result.publicUrl(), "fileUrl must not be null");
        response.key = Objects.requireNonNull(result.key(), "key must not be null");
        response.expiresAt = result.expiresAt();
        return response;
    }
}
