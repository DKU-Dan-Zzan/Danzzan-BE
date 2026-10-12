package com.danzzan.infra.s3;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.danzzan.infra.image.json.ImageUrlSerializer;

public record S3UploadResult(
        String key,
        @JsonSerialize(using = ImageUrlSerializer.class) String url
) {
}

