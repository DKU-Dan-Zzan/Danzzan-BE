package com.danzzan.infra.image;

import com.danzzan.domain.festival.entity.FestivalSetting;
import com.danzzan.domain.festival.repository.FestivalSettingRepository;
import com.danzzan.domain.festival.service.FestivalBrandingService;
import com.danzzan.domain.festival.service.FestivalPosterColorService;
import com.danzzan.domain.festival.service.PosterColorExtractor;
import com.danzzan.domain.festival.theme.dto.ThemeData;
import com.danzzan.domain.festival.theme.dto.ThemeUpsertRequest;
import com.danzzan.domain.festival.theme.repository.ThemeRepository;
import com.danzzan.domain.festival.theme.service.ThemeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LocalFestivalImageFlowTest {
    @TempDir Path directory;

    @Test
    void posterAAndBAreAnalyzedAndKeepIndependentThemeSources() throws Exception {
        LocalImageStorage storage = new LocalImageStorage(directory.toString(), "http://localhost:8080/uploads");
        FestivalSetting setting = FestivalSetting.create("School", "Festival", LocalDate.now(), LocalDate.now(), false);
        ReflectionTestUtils.setField(setting, "id", 1L);
        FestivalSettingRepository settings = mock(FestivalSettingRepository.class);
        ThemeRepository themes = mock(ThemeRepository.class);
        when(settings.findById(1L)).thenReturn(Optional.of(setting));
        when(settings.existsById(1L)).thenReturn(true);
        when(settings.findByIdForUpdate(1L)).thenReturn(Optional.of(setting));
        when(themes.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var writer = new com.danzzan.domain.festival.service.FestivalAppearanceWriter(settings, themes);
        FestivalBrandingService branding = new FestivalBrandingService(settings, storage, writer);
        FestivalPosterColorService analysis = new FestivalPosterColorService(settings, storage, new PosterColorExtractor());
        ThemeService themeService = new ThemeService(settings, themes, storage, writer);

        String posterA = branding.uploadPoster(1L, image(Color.RED)).posterImageUrl();
        String colorA = analysis.extract(1L).colors().get(0).hex();
        var themeA = themeService.create(1L, request("A", posterA, colorA));

        String posterB = branding.uploadPoster(1L, image(Color.BLUE)).posterImageUrl();
        String colorB = analysis.extract(1L).colors().get(0).hex();
        var themeB = themeService.create(1L, request("B", posterB, colorB));

        assertEquals("#FF0000", colorA);
        assertEquals("#0000FF", colorB);
        assertEquals(posterA, themeA.sourcePosterUrl());
        assertEquals(posterB, themeB.sourcePosterUrl());
        assertEquals(posterB, setting.getPosterImageUrl());
        assertNotEquals(posterA, posterB);
        assertTrue(storage.readPoster(1L, posterA).length > 0);
        assertTrue(storage.readPoster(1L, posterB).length > 0);
    }

    private ThemeUpsertRequest request(String name, String poster, String color) {
        ThemeData data = new ThemeData(
                new ThemeData.Brand("#FF642D", "#F5E3A3", "#FF642D", "#E13600"),
                new ThemeData.Status("#2E7D32", "#B26A00", "#B3261E", "#E3776A"),
                new ThemeData.Category("#FF642D", "#EC4899", "#10B981", "#F6CA3B", "#8B5CF6", "#3B82F6"));
        return new ThemeUpsertRequest(name, data, poster, color, List.of(color));
    }

    private MockMultipartFile image(Color color) throws Exception {
        BufferedImage picture = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) picture.setRGB(x, y, color.getRGB());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(picture, "png", output);
        return new MockMultipartFile("file", "official.png", "image/png", output.toByteArray());
    }
}
