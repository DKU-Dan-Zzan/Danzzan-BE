package com.danzzan.domain.notice.dto.response;

import com.danzzan.infra.s3.S3UploadResult;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class NoticeImageUploadResponse {
    private String imageUrl;
    private String key;

    public static NoticeImageUploadResponse from(S3UploadResult result) {
        NoticeImageUploadResponse res = new NoticeImageUploadResponse();
        res.imageUrl = result.url();
        res.key = result.key();
        return res;
    }
}

