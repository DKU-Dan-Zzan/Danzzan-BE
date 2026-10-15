package com.danzzan.domain.festival.background;

import com.danzzan.domain.festival.entity.FestivalSetting;
import com.danzzan.domain.festival.repository.FestivalSettingRepository;
import com.danzzan.infra.image.ImageStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PosterBackgroundGenerationTest {
    @Test void returnsGeneratedArtworkInsteadOfTheOriginalPosterForBothLayouts() throws Exception {
        var mapper = new ObjectMapper();
        var settings = mock(FestivalSettingRepository.class);
        var storage = mock(ImageStorage.class);
        var setting = FestivalSetting.create("school", "festival", LocalDate.now(), LocalDate.now(), true);
        setting.updatePosterImageUrl("official-poster");
        when(settings.findById(1L)).thenReturn(Optional.of(setting));
        byte[] poster = solid(Color.RED), artwork = solid(Color.BLUE);
        when(storage.readPoster(1L, "official-poster")).thenReturn(poster);
        try (var server = new MockWebServer()) {
            server.start();
            var client = new OpenAiPosterBackgroundClient(mapper, "test-key", "test-model", server.url("/v1/images/edits").toString());
            var service = new PosterBackgroundService(settings, storage, client, new PosterBackgroundRenderer());
            for (String target : List.of("ticket", "closed")) {
                server.enqueue(new MockResponse().setBody(mapper.writeValueAsString(Map.of(
                        "data", List.of(Map.of("b64_json", Base64.getEncoder().encodeToString(artwork)))))));
                var result = service.recommend(target, "official-poster");
                var image = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(result.imageBase64())));
                var center = new Color(image.getRGB(image.getWidth() / 2, image.getHeight() / 2));
                assertTrue(center.getBlue() > 240 && center.getRed() < 15, "must publish AI artwork, not a crop of the red poster");
                assertEquals(target.equals("ticket") ? 800 : 720, image.getWidth());
                assertEquals(target.equals("ticket") ? 360 : 1440, image.getHeight());
                assertEquals("official-poster", result.sourcePosterUrl());
            }
        }
        assertNull(setting.getTicketCardBackgroundImageUrl());
        assertNull(setting.getTicketingBackgroundImageUrl());
        verify(settings, never()).save(any());
    }

    static byte[] solid(Color color) throws IOException {
        var image = new BufferedImage(60, 90, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try { graphics.setColor(color); graphics.fillRect(0, 0, 60, 90); }
        finally { graphics.dispose(); }
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
