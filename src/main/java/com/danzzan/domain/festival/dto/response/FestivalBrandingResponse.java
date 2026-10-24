package com.danzzan.domain.festival.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.danzzan.infra.image.json.ImageUrlSerializer;
import com.danzzan.domain.festival.entity.FestivalSetting;

public record FestivalBrandingResponse(Long festivalSettingId, @JsonSerialize(using = ImageUrlSerializer.class) String posterImageUrl, @JsonSerialize(using = ImageUrlSerializer.class) String logoImageUrl) {
    public static FestivalBrandingResponse of(FestivalSetting setting) {
        return new FestivalBrandingResponse(setting.getId(), setting.getPosterImageUrl(), setting.getLogoImageUrl());
    }
}
