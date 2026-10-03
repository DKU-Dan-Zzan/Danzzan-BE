package com.danzzan.domain.festival.dto.response;

import com.danzzan.domain.festival.entity.FestivalSetting;

public record FestivalBrandingResponse(Long festivalSettingId, String posterImageUrl, String logoImageUrl) {
    public static FestivalBrandingResponse of(FestivalSetting setting) {
        return new FestivalBrandingResponse(setting.getId(), setting.getPosterImageUrl(), setting.getLogoImageUrl());
    }
}
