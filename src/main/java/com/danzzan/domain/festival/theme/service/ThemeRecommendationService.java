package com.danzzan.domain.festival.theme.service;

import com.danzzan.domain.festival.repository.FestivalSettingRepository;
import com.danzzan.domain.festival.theme.dto.ThemeData;
import com.danzzan.domain.festival.theme.dto.ThemeRecommendationRequest;
import com.danzzan.domain.festival.theme.exception.ThemeRecommendationException;
import com.danzzan.infra.image.ImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ThemeRecommendationService {
    private final FestivalSettingRepository settings;
    private final ImageStorage imageStorage;
    private final ThemeRecommendationClient client;
    private final ThemeRecommendationValidator validator;

    public ThemeData recommend(Long festivalSettingId, ThemeRecommendationRequest request) {
        var setting = settings.findById(festivalSettingId).orElseThrow(() ->
                new ThemeRecommendationException(HttpStatus.NOT_FOUND, "FESTIVAL_SETTING_NOT_FOUND",
                        "Festival setting not found."));
        String posterUrl = setting.getPosterImageUrl();
        if (posterUrl == null || posterUrl.isBlank()) throw new ThemeRecommendationException(
                HttpStatus.CONFLICT, "POSTER_NOT_FOUND", "Upload an official poster first.");
        byte[] poster = imageStorage.readPoster(festivalSettingId, posterUrl);
        ThemeData result = client.recommend(poster, request.sourceColors(), request.selectedBaseColor());
        validator.validate(result);
        return result;
    }
}
