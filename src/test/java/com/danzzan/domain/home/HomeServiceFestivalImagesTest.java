package com.danzzan.domain.home;

import com.danzzan.domain.home.model.entity.HomeImage;
import com.danzzan.domain.home.repository.HomeImageRepository;
import com.danzzan.domain.home.repository.LineupImageRepository;
import com.danzzan.domain.home.service.HomeService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class HomeServiceFestivalImagesTest {
    @Test void legacyRouteUsesCurrentFestivalAndOtherFestivalIsIsolated() {
        HomeImageRepository images = mock(HomeImageRepository.class);
        HomeService service = new HomeService(images, mock(LineupImageRepository.class));
        HomeImage first = image(1L, "https://cdn/home-wide-1.png");
        HomeImage second = image(2L, "https://cdn/home-wide-2.png");
        when(images.findAllByFestivalSettingIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(first));
        when(images.findAllByFestivalSettingIdOrderByDisplayOrderAsc(2L)).thenReturn(List.of(second));

        assertEquals("https://cdn/home-wide-1.png", service.getHomeImages().get(0).getImageUrl());
        assertEquals("https://cdn/home-wide-2.png", service.getHomeImages(2L).get(0).getImageUrl());
        verify(images).findAllByFestivalSettingIdOrderByDisplayOrderAsc(1L);
        verify(images).findAllByFestivalSettingIdOrderByDisplayOrderAsc(2L);
    }

    private HomeImage image(Long id, String url) {
        HomeImage image = mock(HomeImage.class);
        when(image.getId()).thenReturn(id);
        when(image.getImageUrl()).thenReturn(url);
        return image;
    }
}
