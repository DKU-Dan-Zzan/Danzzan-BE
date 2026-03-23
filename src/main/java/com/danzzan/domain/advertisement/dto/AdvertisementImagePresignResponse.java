package com.danzzan.domain.advertisement.dto;

import com.danzzan.infra.s3.S3PresignedPutResult;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Getter
@NoArgsConstructor
public class AdvertisementImagePresignResponse {

    private String presignedUrl;
    private String imageUrl;
    private String key;
    private Instant expiresAt;
    private String method = "PUT";

    public static AdvertisementImagePresignResponse from(S3PresignedPutResult result) {
        Objects.requireNonNull(result, "presign result must not be null");
        AdvertisementImagePresignResponse response = new AdvertisementImagePresignResponse();
        response.presignedUrl = Objects.requireNonNull(result.uploadUrl(), "presignedUrl must not be null");
        response.imageUrl = Objects.requireNonNull(result.publicUrl(), "imageUrl must not be null");
        response.key = result.key();
        response.expiresAt = result.expiresAt();
        return response;
    }
}
