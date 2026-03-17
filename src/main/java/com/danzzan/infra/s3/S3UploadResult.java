package com.danzzan.infra.s3;

public record S3UploadResult(
        String key,
        String url
) {
}

