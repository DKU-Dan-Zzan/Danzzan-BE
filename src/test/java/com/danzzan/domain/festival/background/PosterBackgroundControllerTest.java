package com.danzzan.domain.festival.background;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PosterBackgroundControllerTest {
    @Test void rejectsStaleSourceBeforeCallingStorageOrAi() throws Exception {
        var settings = mock(com.danzzan.domain.festival.repository.FestivalSettingRepository.class);
        var storage = mock(com.danzzan.infra.image.ImageStorage.class);
        var ai = mock(OpenAiPosterBackgroundClient.class);
        var setting = com.danzzan.domain.festival.entity.FestivalSetting.create("school", "festival",
                java.time.LocalDate.now(), java.time.LocalDate.now(), true);
        setting.updatePosterImageUrl("https://cdn/current.png");
        when(settings.findById(1L)).thenReturn(java.util.Optional.of(setting));
        var service = new PosterBackgroundService(settings, storage, ai, new PosterBackgroundRenderer());
        var mvc = MockMvcBuilders.standaloneSetup(new PosterBackgroundController(service)).build();
        mvc.perform(post("/admin/festival/ticketing-background/recommend").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"ticket\",\"sourcePosterUrl\":\"https://cdn/old.png\"}"))
                .andExpect(status().isConflict());
        verifyNoInteractions(storage, ai);
    }

    @Test void reportsActualSnapshotEvenIfSavedPosterChangesDuringGeneration() throws Exception {
        var settings = mock(com.danzzan.domain.festival.repository.FestivalSettingRepository.class);
        var storage = mock(com.danzzan.infra.image.ImageStorage.class);
        var ai = mock(OpenAiPosterBackgroundClient.class);
        var setting = com.danzzan.domain.festival.entity.FestivalSetting.create("school", "festival",
                java.time.LocalDate.now(), java.time.LocalDate.now(), true);
        setting.updatePosterImageUrl("https://cdn/current.png");
        when(settings.findById(1L)).thenReturn(java.util.Optional.of(setting));
        var bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(20, 30, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", bytes);
        when(storage.readPoster(1L, "https://cdn/current.png")).thenAnswer(invocation -> {
            setting.updatePosterImageUrl("https://cdn/new.png");
            return bytes.toByteArray();
        });
        when(ai.recommend(any(), eq("ticket"))).thenReturn(PosterBackgroundGenerationTest.solid(java.awt.Color.BLUE));
        var mvc = MockMvcBuilders.standaloneSetup(new PosterBackgroundController(
                new PosterBackgroundService(settings, storage, ai, new PosterBackgroundRenderer()))).build();
        for (String guard : new String[]{"", ",\"sourcePosterUrl\":\"https://cdn/current.png\""}) {
            setting.updatePosterImageUrl("https://cdn/current.png");
            mvc.perform(post("/admin/festival/ticketing-background/recommend").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"target\":\"ticket\"" + guard + "}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sourcePosterUrl").value("https://cdn/current.png"));
        }
    }

    @Test void validatesTargetBeforeCallingAiAndMapsPosterFailures() throws Exception {
        var service = mock(PosterBackgroundService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new PosterBackgroundController(service)).build();
        for (String body : new String[]{"{}", "{\"target\":\"unknown\"}",
                "{\"target\":\"ticket\",\"sourcePosterUrl\":\"\"}",
                "{\"target\":\"closed\",\"sourcePosterUrl\":\"javascript:invalid\"}"}) {
            mvc.perform(post("/admin/festival/ticketing-background/recommend").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
        when(service.recommend("ticket")).thenThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "공식 포스터를 먼저 저장해 주세요."));
        mvc.perform(post("/admin/festival/ticketing-background/recommend").contentType(MediaType.APPLICATION_JSON).content("{\"target\":\"ticket\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.message").value("공식 포스터를 먼저 저장해 주세요."));
    }
}
