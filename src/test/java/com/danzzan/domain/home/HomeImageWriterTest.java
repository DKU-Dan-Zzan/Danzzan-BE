package com.danzzan.domain.home;

import com.danzzan.domain.festival.entity.FestivalSetting;
import com.danzzan.domain.festival.repository.FestivalSettingRepository;
import com.danzzan.domain.home.model.entity.HomeImage;
import com.danzzan.domain.home.repository.HomeImageRepository;
import com.danzzan.domain.home.service.HomeImageWriter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HomeImageWriterTest {
    private final FestivalSettingRepository settings = mock(FestivalSettingRepository.class);
    private final HomeImageRepository images = mock(HomeImageRepository.class);
    private final HomeImageWriter writer = new HomeImageWriter(settings, images);

    @Test void replacesOnlyTheFirstImageWhenTheGeneratedPosterIsStillCurrent() {
        FestivalSetting setting = FestivalSetting.create("school", "festival", LocalDate.now(), LocalDate.now(), false);
        setting.updatePosterImageUrl("poster-current");
        when(settings.findByIdForUpdate(1L)).thenReturn(Optional.of(setting));
        HomeImage first = image(10L, "first", 3);
        HomeImage second = image(11L, "second", 8);
        when(images.findAllByFestivalSettingIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(first, second));

        var result = writer.replaceFirst(1L, "staged", 10L, "first", "poster-current");

        assertEquals(10L, result.id());
        assertEquals("staged", first.getImageUrl());
        assertEquals(3, first.getDisplayOrder());
        assertEquals("second", second.getImageUrl());
        assertEquals(8, second.getDisplayOrder());
    }

    @Test void rejectsAGeneratedBannerWhenItsPosterOrFirstImageIsStale() {
        FestivalSetting setting = FestivalSetting.create("school", "festival", LocalDate.now(), LocalDate.now(), false);
        setting.updatePosterImageUrl("poster-current");
        when(settings.findByIdForUpdate(1L)).thenReturn(Optional.of(setting));
        HomeImage first = image(10L, "first", 0);
        when(images.findAllByFestivalSettingIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(first));

        ResponseStatusException posterConflict = assertThrows(ResponseStatusException.class,
                () -> writer.replaceFirst(1L, "staged", 10L, "first", "poster-old"));
        assertEquals(HttpStatus.CONFLICT, posterConflict.getStatusCode());
        assertEquals("first", first.getImageUrl());

        ResponseStatusException imageConflict = assertThrows(ResponseStatusException.class,
                () -> writer.replaceFirst(1L, "staged", 99L, "first", "poster-current"));
        assertEquals(HttpStatus.CONFLICT, imageConflict.getStatusCode());
        assertEquals("first", first.getImageUrl());
    }

    private HomeImage image(Long id, String url, int order) {
        HomeImage image = new HomeImage(url, 1L, order);
        ReflectionTestUtils.setField(image, "id", id);
        return image;
    }
}
