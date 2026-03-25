package com.danzzan.domain.advertisement.dto.response;

import com.danzzan.infra.s3.S3PresignedPutResult;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

/**
 * 공지 {@link com.danzzan.domain.notice.dto.response.NoticeImagePresignResponse}와 동일한 JSON 키를 씁니다.
 */
@Getter
@NoArgsConstructor
public class AdvertisementImagePresignResponse {

    /**
     * S3 Presigned PUT URL.
     */
    private String presignedUrl;

    /**
     * 업로드 완료 후 이미지 URL로 저장할 값.
     */
    private String fileUrl;

    private String key;
    private Instant expiresAt;
    private String method = "PUT";

    public static AdvertisementImagePresignResponse from(S3PresignedPutResult result) {
        Objects.requireNonNull(result, "presign result must not be null");
        AdvertisementImagePresignResponse res = new AdvertisementImagePresignResponse();
        res.presignedUrl = Objects.requireNonNull(result.uploadUrl(), "presignedUrl must not be null");
        res.fileUrl = Objects.requireNonNull(result.publicUrl(), "fileUrl must not be null");
        res.key = Objects.requireNonNull(result.key(), "key must not be null");
        res.expiresAt = result.expiresAt();
        return res;
    }
}
