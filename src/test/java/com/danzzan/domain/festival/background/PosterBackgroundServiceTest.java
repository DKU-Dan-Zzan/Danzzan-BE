package com.danzzan.domain.festival.background;

import com.danzzan.domain.festival.entity.FestivalSetting;
import com.danzzan.domain.festival.repository.FestivalSettingRepository;
import com.danzzan.infra.image.ImageStorage;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PosterBackgroundServiceTest {
    final FestivalSettingRepository settings = mock(FestivalSettingRepository.class);
    final ImageStorage storage = mock(ImageStorage.class);
    final OpenAiPosterBackgroundClient ai = mock(OpenAiPosterBackgroundClient.class);
    final PosterBackgroundService service = new PosterBackgroundService(settings, storage, ai, new PosterBackgroundRenderer());
    FestivalSetting setting() { return FestivalSetting.create("school", "festival", LocalDate.now(), LocalDate.now(), true); }
    @Test void usesOnlyStoredOfficialPosterAndNeverPublishesSettings() throws Exception {
        var setting = setting(); setting.updatePosterImageUrl("https://cdn/festival/1/poster/official.png");
        when(settings.findById(1L)).thenReturn(Optional.of(setting));
        var bytes = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(20, 30, BufferedImage.TYPE_INT_RGB), "png", bytes);
        byte[] poster = bytes.toByteArray();
        when(storage.readPoster(1L, setting.getPosterImageUrl())).thenReturn(poster);
        when(ai.recommend(poster, "ticket")).thenReturn(PosterBackgroundGenerationTest.solid(java.awt.Color.BLUE));
        var result = service.recommend("ticket");
        assertEquals(800, result.width()); assertEquals(360, result.height()); assertFalse(result.imageBase64().isEmpty());
        assertNull(setting.getTicketCardBackgroundImageUrl()); assertNull(setting.getTicketingBackgroundImageUrl());
        verify(storage).readPoster(1L, setting.getPosterImageUrl()); verify(settings, never()).save(any());
    }
    @Test void malformedGeneratedImageDoesNotReplaceExistingBackgrounds() throws Exception {
        var setting = setting(); setting.updatePosterImageUrl("official");
        setting.updateTicketingBackgroundImageUrl("existing-closed.jpg");
        setting.updateTicketCardBackgroundImageUrl("existing-ticket.jpg");
        when(settings.findById(1L)).thenReturn(Optional.of(setting));
        byte[] poster = PosterBackgroundGenerationTest.solid(java.awt.Color.RED);
        when(storage.readPoster(1L, "official")).thenReturn(poster);
        when(ai.recommend(poster, "closed")).thenReturn(new byte[]{1, 2, 3});
        var error = assertThrows(com.danzzan.domain.festival.theme.exception.ThemeRecommendationException.class,
                () -> service.recommend("closed"));
        assertEquals("OPENAI_MALFORMED_OUTPUT", error.getCode());
        assertEquals(org.springframework.http.HttpStatus.BAD_GATEWAY, error.getStatus());
        verify(settings, never()).save(any());
        assertEquals("existing-closed.jpg", setting.getTicketingBackgroundImageUrl());
        assertEquals("existing-ticket.jpg", setting.getTicketCardBackgroundImageUrl());
    }
    @Test void missingOrInvalidPosterNeverCallsAi() {
        when(settings.findById(1L)).thenReturn(Optional.of(setting()));
        assertThrows(ResponseStatusException.class, () -> service.recommend("ticket"));
        verifyNoInteractions(storage, ai);
        var setting = setting(); setting.updatePosterImageUrl("official");
        when(settings.findById(1L)).thenReturn(Optional.of(setting));
        when(storage.readPoster(1L, "official")).thenReturn(new byte[]{1});
        assertThrows(ResponseStatusException.class, () -> service.recommend("closed"));
        verifyNoInteractions(ai);
    }
}
