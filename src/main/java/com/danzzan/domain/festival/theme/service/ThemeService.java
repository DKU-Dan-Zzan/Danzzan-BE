package com.danzzan.domain.festival.theme.service;

import com.danzzan.domain.festival.entity.FestivalSetting;
import com.danzzan.domain.festival.service.FestivalAppearanceWriter;
import com.danzzan.domain.festival.repository.FestivalSettingRepository;
import com.danzzan.domain.festival.theme.dto.ThemeResponse;
import com.danzzan.domain.festival.theme.dto.ThemeBundleRequest;
import com.danzzan.domain.festival.theme.dto.ThemeUpsertRequest;
import com.danzzan.domain.festival.theme.entity.Theme;
import com.danzzan.domain.festival.theme.exception.ThemeException;
import com.danzzan.domain.festival.theme.repository.ThemeRepository;
import com.danzzan.infra.image.ImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ThemeService {
    private final FestivalSettingRepository festivalSettingRepository;
    private final ThemeRepository themeRepository;
    private final ImageStorage imageStorage;
    private final FestivalAppearanceWriter appearanceWriter;

    public List<ThemeResponse> list(Long festivalSettingId) {
        FestivalSetting setting = findSetting(festivalSettingId);
        return themeRepository.findByFestivalSetting_IdOrderByIdDesc(festivalSettingId).stream()
                .map(theme -> ThemeResponse.of(theme, setting.getActiveThemeId()))
                .toList();
    }

    public ThemeResponse get(Long festivalSettingId, Long themeId) {
        Theme theme = findTheme(festivalSettingId, themeId);
        return ThemeResponse.of(theme, theme.getFestivalSetting().getActiveThemeId());
    }

    public Optional<ThemeResponse> active(Long festivalSettingId) {
        FestivalSetting setting = findSetting(festivalSettingId);
        if (setting.getActiveThemeId() == null) return Optional.empty();
        return Optional.of(ThemeResponse.of(
                findTheme(festivalSettingId, setting.getActiveThemeId()), setting.getActiveThemeId()));
    }

    @Transactional
    public ThemeResponse create(Long festivalSettingId, ThemeUpsertRequest request) {
        FestivalSetting setting = findSetting(festivalSettingId);
        Theme theme = themeRepository.save(Theme.create(setting, request.name(), ThemeColorPolicy.normalize(request.themeData()),
                request.sourcePosterUrl(), request.selectedBaseColor(), request.sourceColors()));
        return ThemeResponse.of(theme, setting.getActiveThemeId());
    }

    @Transactional
    public ThemeResponse update(Long festivalSettingId, Long themeId, ThemeUpsertRequest request) {
        Theme theme = findTheme(festivalSettingId, themeId);
        theme.update(request.name(), ThemeColorPolicy.normalize(request.themeData()));
        return ThemeResponse.of(theme, theme.getFestivalSetting().getActiveThemeId());
    }

    @Transactional
    public void delete(Long festivalSettingId, Long themeId) {
        FestivalSetting setting = lockSetting(festivalSettingId);
        Theme theme = findTheme(festivalSettingId, themeId);
        if (Objects.equals(setting.getActiveThemeId(), themeId)) throw ThemeException.activeThemeDeletion();
        themeRepository.delete(theme);
    }

    @Transactional
    public ThemeResponse activate(Long festivalSettingId, Long themeId) {
        FestivalSetting setting = lockSetting(festivalSettingId);
        Theme theme = themeRepository.findById(themeId).orElseThrow(ThemeException::themeNotFound);
        if (!Objects.equals(theme.getFestivalSetting().getId(), festivalSettingId)) {
            throw ThemeException.wrongFestival();
        }
        setting.activateTheme(themeId);
        festivalSettingRepository.save(setting);
        return ThemeResponse.of(theme, themeId);
    }

    @Transactional
    public void activateDefault(Long festivalSettingId) {
        FestivalSetting setting = lockSetting(festivalSettingId);
        setting.activateTheme(null);
        festivalSettingRepository.save(setting);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ThemeResponse applyBundle(Long festivalSettingId, ThemeBundleRequest request) {
        // The background upload and recommendation APIs operate on the singleton festival.
        if (!FestivalSetting.SINGLETON_ID.equals(festivalSettingId)) throw ThemeException.wrongFestival();
        if (!festivalSettingRepository.existsById(festivalSettingId)) throw ThemeException.settingNotFound();
        // Scalar preflight avoids retaining a stale managed entity while storage requests are in flight.
        String poster = festivalSettingRepository.findPosterImageUrlById(festivalSettingId).orElse(null);
        if (!Objects.equals(poster, request.sourcePosterUrl())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "공식 포스터가 변경되었습니다. 다시 미리보기해 주세요.");
        }
        imageStorage.validateTicketingBackground(festivalSettingId, request.ticketCardBackgroundImageUrl());
        imageStorage.validateTicketingBackground(festivalSettingId, request.ticketingBackgroundImageUrl());
        if (request.homeBanner() != null) imageStorage.validateHomeImage(festivalSettingId, request.homeBanner().imageUrl());
        return appearanceWriter.applyBundle(festivalSettingId, normalizedBundle(request));
    }

    private FestivalSetting findSetting(Long id) {
        return festivalSettingRepository.findById(id).orElseThrow(ThemeException::settingNotFound);
    }

    private FestivalSetting lockSetting(Long id) {
        return festivalSettingRepository.findByIdForUpdate(id).orElseThrow(ThemeException::settingNotFound);
    }

    private Theme findTheme(Long festivalSettingId, Long themeId) {
        return themeRepository.findByIdAndFestivalSetting_Id(themeId, festivalSettingId)
                .orElseThrow(ThemeException::themeNotFound);
    }

    private ThemeBundleRequest normalizedBundle(ThemeBundleRequest request) {
        return new ThemeBundleRequest(request.name(), ThemeColorPolicy.normalize(request.themeData()),
                request.sourcePosterUrl(), request.sourceColors(), request.selectedBaseColor(),
                request.ticketCardBackgroundImageUrl(), request.ticketingBackgroundImageUrl(), request.homeBanner());
    }
}
