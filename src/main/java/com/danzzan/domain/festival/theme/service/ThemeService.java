package com.danzzan.domain.festival.theme.service;

import com.danzzan.domain.festival.entity.FestivalSetting;
import com.danzzan.domain.festival.repository.FestivalSettingRepository;
import com.danzzan.domain.festival.theme.dto.ThemeResponse;
import com.danzzan.domain.festival.theme.dto.ThemeUpsertRequest;
import com.danzzan.domain.festival.theme.entity.Theme;
import com.danzzan.domain.festival.theme.exception.ThemeException;
import com.danzzan.domain.festival.theme.repository.ThemeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ThemeService {
    private final FestivalSettingRepository festivalSettingRepository;
    private final ThemeRepository themeRepository;

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
        Theme theme = themeRepository.save(Theme.create(setting, request.name(), request.themeData(),
                request.sourcePosterUrl(), request.selectedBaseColor(), request.sourceColors()));
        return ThemeResponse.of(theme, setting.getActiveThemeId());
    }

    @Transactional
    public ThemeResponse update(Long festivalSettingId, Long themeId, ThemeUpsertRequest request) {
        Theme theme = findTheme(festivalSettingId, themeId);
        theme.update(request.name(), request.themeData());
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
}
