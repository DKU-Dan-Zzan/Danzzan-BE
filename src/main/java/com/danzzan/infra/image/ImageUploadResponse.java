package com.danzzan.infra.image;

import com.danzzan.infra.image.json.ImageUrlSerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

public record ImageUploadResponse(@JsonSerialize(using = ImageUrlSerializer.class) String imageUrl, String key) { }
