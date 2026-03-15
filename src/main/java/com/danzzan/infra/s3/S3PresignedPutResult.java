package com.danzzan.infra.s3;

import java.time.Instant;

public record S3PresignedPutResult(
        String key,
        String publicUrl,
        String uploadUrl,
        Instant expiresAt
) {
}

