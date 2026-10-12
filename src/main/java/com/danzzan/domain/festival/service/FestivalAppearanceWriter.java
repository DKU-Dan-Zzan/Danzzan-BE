package com.danzzan.domain.festival.service;

import com.danzzan.domain.festival.dto.response.FestivalBrandingResponse;
import com.danzzan.domain.festival.entity.FestivalSetting;
import com.danzzan.domain.festival.repository.FestivalSettingRepository;
import com.danzzan.domain.festival.theme.dto.ThemeBundleRequest;
import com.danzzan.domain.festival.theme.dto.ThemeResponse;
import com.danzzan.domain.festival.theme.entity.Theme;
import com.danzzan.domain.festival.theme.exception.ThemeException;
import com.danzzan.domain.festival.theme.repository.ThemeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

/** Short database-only transactions, entered after storage work has completed. */
@Service
@RequiredArgsConstructor
@Transactional
public class FestivalAppearanceWriter {
    private final FestivalSettingRepository settings;
    private final ThemeRepository themes;

    public FestivalBrandingResponse updatePoster(Long festivalSettingId, String url) {
        FestivalSetting setting = lockSetting(festivalSettingId);
        setting.updatePosterImageUrl(url);
        return FestivalBrandingResponse.of(setting);
    }

    public FestivalBrandingResponse updateLogo(Long festivalSettingId, String url) {
        FestivalSetting setting = lockSetting(festivalSettingId);
        setting.updateLogoImageUrl(url);
        return FestivalBrandingResponse.of(setting);
    }

    public ThemeResponse applyBundle(Long festivalSettingId, ThemeBundleRequest request) {
        FestivalSetting setting = lockSetting(festivalSettingId);
        // Storage verification may take time: the authoritative source check belongs under the lock.
        if (!Objects.equals(setting.getPosterImageUrl(), request.sourcePosterUrl())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "공식 포스터가 변경되었습니다. 다시 미리보기해 주세요.");
        }
        Theme theme = themes.save(Theme.create(setting, request.name(), request.themeData(),
                request.sourcePosterUrl(), request.selectedBaseColor(), request.sourceColors()));
        setting.activateTheme(theme.getId());
        setting.updateTicketCardBackgroundImageUrl(request.ticketCardBackgroundImageUrl());
        setting.updateTicketingBackgroundImageUrl(request.ticketingBackgroundImageUrl());
        settings.save(setting);
        return ThemeResponse.of(theme, theme.getId());
    }

    private FestivalSetting lockSetting(Long id) {
        return settings.findByIdForUpdate(id).orElseThrow(ThemeException::settingNotFound);
    }
}
