package com.danzzan.domain.timetable.dto.admin.response;

import com.danzzan.infra.s3.S3PresignedPutResult;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

/**
 * 공지/광고/Pub의 presign 응답과 동일한 JSON 키 구조.
 */
@Getter
@NoArgsConstructor
public class ArtistImagePresignResponse {

    private String presignedUrl;
    private String fileUrl;
    private String key;
    private Instant expiresAt;
    private String method = "PUT";

    public static ArtistImagePresignResponse from(S3PresignedPutResult result) {
        Objects.requireNonNull(result, "presign result must not be null");
        ArtistImagePresignResponse res = new ArtistImagePresignResponse();
        res.presignedUrl = Objects.requireNonNull(result.uploadUrl(), "presignedUrl must not be null");
        res.fileUrl = Objects.requireNonNull(result.publicUrl(), "fileUrl must not be null");
        res.key = Objects.requireNonNull(result.key(), "key must not be null");
        res.expiresAt = result.expiresAt();
        return res;
    }
}
