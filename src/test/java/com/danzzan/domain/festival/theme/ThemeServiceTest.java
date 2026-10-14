package com.danzzan.domain.festival.theme;

import com.danzzan.domain.festival.entity.FestivalSetting;
import com.danzzan.domain.festival.repository.FestivalSettingRepository;
import com.danzzan.domain.festival.theme.dto.ThemeData;
import com.danzzan.domain.festival.theme.dto.ThemeResponse;
import com.danzzan.domain.festival.theme.dto.ThemeUpsertRequest;
import com.danzzan.domain.festival.theme.dto.PublicThemeResponse;
import com.danzzan.domain.festival.theme.entity.Theme;
import com.danzzan.domain.festival.theme.exception.ThemeException;
import com.danzzan.domain.festival.theme.repository.ThemeRepository;
import com.danzzan.domain.festival.theme.service.ThemeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ThemeServiceTest {
    @Mock FestivalSettingRepository festivalSettingRepository;
    @Mock ThemeRepository themeRepository;
    @InjectMocks ThemeService themeService;

    @Test
    void createsThemeForExistingFestival() {
        FestivalSetting setting = setting(1L);
        when(festivalSettingRepository.findById(1L)).thenReturn(Optional.of(setting));
        when(themeRepository.save(any(Theme.class))).thenAnswer(invocation -> {
            Theme theme = invocation.getArgument(0);
            ReflectionTestUtils.setField(theme, "id", 10L);
            return theme;
        });

        ThemeResponse created = themeService.create(1L, request(" Orange "));

        assertEquals(10L, created.id());
        assertEquals(1L, created.festivalSettingId());
        assertEquals("Orange", created.name());
        assertEquals(data(), created.themeData());
        assertFalse(created.active());
        assertNull(created.sourcePosterUrl());
    }

    @Test
    void preservesPosterSnapshotsWhenTheFestivalPosterChanges() {
        FestivalSetting setting = setting(1L);
        when(festivalSettingRepository.findById(1L)).thenReturn(Optional.of(setting));
        when(themeRepository.save(any(Theme.class))).thenAnswer(invocation -> invocation.getArgument(0));
        String posterA = "https://cdn.example.com/festival/1/poster/a.png";
        String posterB = "https://cdn.example.com/festival/1/poster/b.png";
        setting.updatePosterImageUrl(posterA);
        ThemeResponse first = themeService.create(1L, new ThemeUpsertRequest("A", data(), posterA,
                "#4EB15E", List.of("#4EB15E", "#13730C")));
        setting.updatePosterImageUrl(posterB);
        ThemeResponse second = themeService.create(1L, new ThemeUpsertRequest("B", data(), posterB,
                "#FBE44B", List.of("#FBE44B")));
        assertEquals(posterA, first.sourcePosterUrl());
        assertEquals("#4EB15E", first.selectedBaseColor());
        assertEquals(List.of("#4EB15E", "#13730C"), first.sourceColors());
        assertEquals(posterB, second.sourcePosterUrl());
        assertEquals(posterB, setting.getPosterImageUrl());
    }

    @Test
    void publicActiveResponseDoesNotExposeProvenance() throws Exception {
        Theme theme = Theme.create(setting(1L), "Orange", data(), "https://cdn.example.com/a.png",
                "#4EB15E", List.of("#4EB15E"));
        String json = new ObjectMapper().findAndRegisterModules()
                .writeValueAsString(PublicThemeResponse.from(ThemeResponse.of(theme, null)));
        assertFalse(json.contains("sourcePosterUrl"));
        assertFalse(json.contains("selectedBaseColor"));
        assertFalse(json.contains("sourceColors"));
    }

    @Test
    void listsAndGetsThemesForRequestedFestival() {
        FestivalSetting setting = setting(1L);
        Theme theme = theme(10L, setting);
        setting.activateTheme(10L);
        when(festivalSettingRepository.findById(1L)).thenReturn(Optional.of(setting));
        when(themeRepository.findByFestivalSetting_IdOrderByIdDesc(1L)).thenReturn(List.of(theme));
        when(themeRepository.findByIdAndFestivalSetting_Id(10L, 1L)).thenReturn(Optional.of(theme));

        assertTrue(themeService.list(1L).get(0).active());
        assertEquals("Orange", themeService.get(1L, 10L).name());
        assertTrue(themeService.active(1L).orElseThrow().active());
    }

    @Test
    void updatesThemeWithoutChangingFestival() {
        Theme theme = Theme.create(setting(1L), "Orange", data(), "https://cdn.example.com/a.png",
                "#4EB15E", List.of("#4EB15E"));
        ReflectionTestUtils.setField(theme, "id", 10L);
        when(themeRepository.findByIdAndFestivalSetting_Id(10L, 1L)).thenReturn(Optional.of(theme));

        ThemeResponse updated = themeService.update(1L, 10L, new ThemeUpsertRequest(" New name ", data(),
                "https://cdn.example.com/other.png", "#FFFFFF", List.of("#FFFFFF")));

        assertEquals("New name", updated.name());
        assertEquals(1L, updated.festivalSettingId());
        assertEquals("https://cdn.example.com/a.png", updated.sourcePosterUrl());
        assertEquals("#4EB15E", updated.selectedBaseColor());
    }

    @Test
    void deletesInactiveTheme() {
        FestivalSetting setting = setting(1L);
        Theme theme = theme(10L, setting);
        when(festivalSettingRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(setting));
        when(themeRepository.findByIdAndFestivalSetting_Id(10L, 1L)).thenReturn(Optional.of(theme));

        themeService.delete(1L, 10L);

        verify(themeRepository).delete(theme);
    }

    @Test
    void activatesThemeOwnedByFestival() {
        FestivalSetting setting = setting(1L);
        Theme theme = theme(10L, setting);
        when(festivalSettingRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(setting));
        when(themeRepository.findById(10L)).thenReturn(Optional.of(theme));

        ThemeResponse active = themeService.activate(1L, 10L);

        assertEquals(10L, setting.getActiveThemeId());
        assertTrue(active.active());
        verify(festivalSettingRepository).save(setting);
    }

    @Test
    void rejectsActivationOfAnotherFestivalsTheme() {
        FestivalSetting setting = setting(1L);
        when(festivalSettingRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(setting));
        when(themeRepository.findById(20L)).thenReturn(Optional.of(theme(20L, setting(2L))));

        ThemeException error = assertThrows(ThemeException.class, () -> themeService.activate(1L, 20L));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertNull(setting.getActiveThemeId());
        verify(festivalSettingRepository, never()).save(any());
    }

    @Test
    void activatesDefaultThemeByClearingTheActiveTheme() {
        FestivalSetting setting = setting(1L);
        setting.activateTheme(10L);
        when(festivalSettingRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(setting));

        themeService.activateDefault(1L);

        assertNull(setting.getActiveThemeId());
        verify(festivalSettingRepository).save(setting);
        verifyNoInteractions(themeRepository);
    }

    @Test
    void activatingDefaultThemeIsIdempotent() {
        FestivalSetting setting = setting(1L);
        when(festivalSettingRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(setting));

        themeService.activateDefault(1L);

        assertNull(setting.getActiveThemeId());
        verify(festivalSettingRepository).save(setting);
        verifyNoInteractions(themeRepository);
    }

    @Test
    void rejectsDefaultThemeActivationForMissingFestivalWithoutChangingThemes() {
        when(festivalSettingRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        ThemeException error = assertThrows(ThemeException.class, () -> themeService.activateDefault(99L));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
        verify(festivalSettingRepository, never()).save(any());
        verifyNoInteractions(themeRepository);
    }

    @Test
    void rejectsDeletionOfActiveTheme() {
        FestivalSetting setting = setting(1L);
        setting.activateTheme(10L);
        when(festivalSettingRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(setting));
        when(themeRepository.findByIdAndFestivalSetting_Id(10L, 1L)).thenReturn(Optional.of(theme(10L, setting)));

        ThemeException error = assertThrows(ThemeException.class, () -> themeService.delete(1L, 10L));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        verify(themeRepository, never()).delete(any());
    }

    @Test
    void missingFestivalCannotCreateTheme() {
        when(festivalSettingRepository.findById(99L)).thenReturn(Optional.empty());

        ThemeException error = assertThrows(ThemeException.class, () -> themeService.create(99L, request("Orange")));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
        verifyNoInteractions(themeRepository);
    }

    @Test
    void noActiveThemeUsesFrontendFallback() {
        when(festivalSettingRepository.findById(1L)).thenReturn(Optional.of(setting(1L)));
        assertTrue(themeService.active(1L).isEmpty());
        verifyNoInteractions(themeRepository);
    }

    private FestivalSetting setting(Long id) {
        FestivalSetting setting = FestivalSetting.create("학교", "축제",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), false);
        ReflectionTestUtils.setField(setting, "id", id);
        return setting;
    }

    private Theme theme(Long id, FestivalSetting setting) {
        Theme theme = Theme.create(setting, "Orange", data());
        ReflectionTestUtils.setField(theme, "id", id);
        return theme;
    }

    private ThemeUpsertRequest request(String name) {
        return new ThemeUpsertRequest(name, data());
    }

    private ThemeData data() {
        return new ThemeData(
                new ThemeData.Brand("#FF642D", "#F5E3A3", "#FF642D", "#E13600"),
                new ThemeData.Status("#2E7D32", "#B26A00", "#B3261E", "#E3776A"),
                new ThemeData.Category("#FF642D", "#EC4899", "#10B981", "#F6CA3B", "#8B5CF6", "#3B82F6")
        );
    }
}
