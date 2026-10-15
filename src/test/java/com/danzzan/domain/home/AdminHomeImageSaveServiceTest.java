package com.danzzan.domain.home;

import com.danzzan.domain.festival.repository.FestivalSettingRepository;
import com.danzzan.domain.home.model.dto.AdminHomeImageDto;
import com.danzzan.domain.home.model.dto.HomeImageFirstReplaceRequest;
import com.danzzan.domain.home.repository.HomeImageRepository;
import com.danzzan.domain.home.service.AdminHomeImageService;
import com.danzzan.domain.home.service.HomeImageWriter;
import com.danzzan.infra.image.ImageStorage;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminHomeImageSaveServiceTest {
    private final FestivalSettingRepository settings = mock(FestivalSettingRepository.class);
    private final HomeImageRepository images = mock(HomeImageRepository.class);
    private final ImageStorage storage = mock(ImageStorage.class);
    private final HomeImageWriter writer = mock(HomeImageWriter.class);
    private final AdminHomeImageService service = new AdminHomeImageService(settings, images, storage, writer);

    @Test void savesOnlyAValidatedStagedImageWithThePreviewIdentity() {
        when(settings.existsById(1L)).thenReturn(true);
        var request = new HomeImageFirstReplaceRequest("https://cdn/staged.png", 10L,
                "https://cdn/10.png", "https://cdn/poster.png");
        var saved = new AdminHomeImageDto(10L, "https://cdn/staged.png", 0);
        when(writer.replaceFirst(1L, request.imageUrl(), request.expectedFirstImageId(),
                request.expectedFirstImageUrl(), request.sourcePosterUrl())).thenReturn(saved);

        assertSame(saved, service.replaceFirst(1L, request));
        verify(storage).validateHomeImage(1L, request.imageUrl());
        verify(writer).replaceFirst(1L, request.imageUrl(), request.expectedFirstImageId(),
                request.expectedFirstImageUrl(), request.sourcePosterUrl());
    }

    @Test void rejectsForeignFestivalsAndNeverPublishesAnUnverifiedImage() {
        var request = new HomeImageFirstReplaceRequest("https://cdn/staged.png", null, null, null);
        assertThrows(ResponseStatusException.class, () -> service.replaceFirst(99L, request));
        verifyNoInteractions(storage, writer);

        when(settings.existsById(1L)).thenReturn(true);
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid"))
                .when(storage).validateHomeImage(1L, request.imageUrl());
        assertThrows(ResponseStatusException.class, () -> service.replaceFirst(1L, request));
        verifyNoInteractions(writer);
    }
}
