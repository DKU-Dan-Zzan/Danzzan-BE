package com.danzzan.infra.s3;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.danzzan.infra.image.json.ImageUrlSerializer;
import java.time.Instant;

public record S3PresignedPutResult(
        String key,
        @JsonSerialize(using = ImageUrlSerializer.class) String publicUrl,
        String uploadUrl,
        Instant expiresAt
) {
}
