package com.danzzan.domain.festival;

import com.danzzan.domain.festival.entity.FestivalSetting;
import com.danzzan.domain.festival.repository.FestivalSettingRepository;
import com.danzzan.domain.festival.service.FestivalBrandingService;
import com.danzzan.infra.image.ImageStorage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FestivalBrandingServiceTest {
    private final FestivalSettingRepository settings = mock(FestivalSettingRepository.class);
    private final ImageStorage storage = mock(ImageStorage.class);
    private final FestivalBrandingService service = new FestivalBrandingService(settings, storage,
            new com.danzzan.domain.festival.service.FestivalAppearanceWriter(settings, null));

    private FestivalSetting setting(Long id) {
        FestivalSetting setting = FestivalSetting.create("School", "Festival", LocalDate.now(), LocalDate.now(), false);
        ReflectionTestUtils.setField(setting, "id", id);
        when(settings.findById(id)).thenReturn(Optional.of(setting));
        when(settings.existsById(id)).thenReturn(true);
        when(settings.findByIdForUpdate(id)).thenReturn(Optional.of(setting));
        return setting;
    }

    private MockMultipartFile image() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return new MockMultipartFile("file", "official.png", "image/png", bytes.toByteArray());
    }

    @Test void emptyBrandingAndFestivalIsolation() throws Exception {
        FestivalSetting first = setting(1L);
        FestivalSetting second = setting(2L);
        assertNull(service.get(1L).posterImageUrl());
        assertNull(service.get(2L).logoImageUrl());
        var file = image();
        when(storage.uploadPoster(2L, file)).thenReturn("https://cdn/second.png");
        assertEquals("https://cdn/second.png", service.uploadPoster(2L, file).posterImageUrl());
        assertNull(first.getPosterImageUrl());
        assertEquals("https://cdn/second.png", second.getPosterImageUrl());
    }

    @Test void replacesOnlyRequestedImageAfterSuccessfulUpload() throws Exception {
        FestivalSetting setting = setting(1L);
        setting.updatePosterImageUrl("https://cdn/old-poster.png");
        setting.updateLogoImageUrl("https://cdn/old-logo.png");
        var file = image();
        when(storage.uploadLogo(1L, file)).thenReturn("https://cdn/new-logo.png");
        var response = service.uploadLogo(1L, file);
        assertEquals("https://cdn/old-poster.png", response.posterImageUrl());
        assertEquals("https://cdn/new-logo.png", response.logoImageUrl());
    }

    @Test void uploadFailureAndInvalidFileKeepExistingUrl() throws Exception {
        FestivalSetting setting = setting(1L);
        setting.updatePosterImageUrl("https://cdn/old.png");
        var file = image();
        when(storage.uploadPoster(1L, file)).thenThrow(new IllegalStateException("storage unavailable"));
        assertThrows(IllegalStateException.class, () -> service.uploadPoster(1L, file));
        assertEquals("https://cdn/old.png", setting.getPosterImageUrl());
        var fake = new MockMultipartFile("file", "fake.png", "image/png", "not an image".getBytes());
        assertThrows(ResponseStatusException.class, () -> service.uploadPoster(1L, fake));
        assertEquals("https://cdn/old.png", setting.getPosterImageUrl());
    }

    @Test void missingFestivalFailsBeforeStorage() throws Exception {
        when(settings.existsById(404L)).thenReturn(false);
        assertThrows(ResponseStatusException.class, () -> service.uploadPoster(404L, image()));
        verifyNoInteractions(storage);
    }

    @Test void oversizedPosterAndLogoKeepExistingUrlsAndNeverUpload() {
        FestivalSetting setting = setting(1L);
        setting.updatePosterImageUrl("https://cdn/old-poster.png");
        setting.updateLogoImageUrl("https://cdn/old-logo.png");
        var file = new MockMultipartFile("file", "large.png", "image/png", new byte[5 * 1024 * 1024 + 1]);
        assertThrows(ResponseStatusException.class, () -> service.uploadPoster(1L, file));
        assertThrows(ResponseStatusException.class, () -> service.uploadLogo(1L, file));
        assertEquals("https://cdn/old-poster.png", setting.getPosterImageUrl());
        assertEquals("https://cdn/old-logo.png", setting.getLogoImageUrl());
        verifyNoInteractions(storage);
    }
}
