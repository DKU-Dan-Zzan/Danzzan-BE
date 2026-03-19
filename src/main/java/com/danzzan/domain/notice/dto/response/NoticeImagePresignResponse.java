package com.danzzan.domain.notice.dto.response;

import com.danzzan.infra.s3.S3PresignedPutResult;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Getter
@NoArgsConstructor
public class NoticeImagePresignResponse {
    /**
     * S3 Presigned PUT URL.
     * 프론트에서 res.data.presignedUrl 로 접근합니다.
     */
    private String presignedUrl;

    /**
     * 업로드 완료 후 접근할 파일 URL.
     * 프론트에서 res.data.fileUrl 로 접근합니다.
     */
    private String fileUrl;

    private String key;
    private Instant expiresAt;
    private String method = "PUT";

    public static NoticeImagePresignResponse from(S3PresignedPutResult result) {
        Objects.requireNonNull(result, "presign result must not be null");
        NoticeImagePresignResponse res = new NoticeImagePresignResponse();
        res.presignedUrl = Objects.requireNonNull(result.uploadUrl(), "presignedUrl must not be null");
        res.fileUrl = Objects.requireNonNull(result.publicUrl(), "fileUrl must not be null");
        res.key = result.key();
        res.expiresAt = result.expiresAt();
        return res;
    }
}

