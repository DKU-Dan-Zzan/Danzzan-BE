package com.danzzan.domain.festival.theme;

import com.danzzan.domain.festival.entity.FestivalSetting;
import com.danzzan.domain.festival.repository.FestivalSettingRepository;
import com.danzzan.domain.festival.theme.dto.ThemeData;
import com.danzzan.domain.festival.theme.dto.ThemeRecommendationRequest;
import com.danzzan.domain.festival.theme.exception.ThemeRecommendationException;
import com.danzzan.domain.festival.theme.service.OpenAiThemeRecommendationClient;
import com.danzzan.domain.festival.theme.service.ThemeRecommendationClient;
import com.danzzan.domain.festival.theme.service.ThemeRecommendationService;
import com.danzzan.domain.festival.theme.service.ThemeRecommendationValidator;
import com.danzzan.infra.image.ImageStorage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ThemeRecommendationTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
    private final ThemeRecommendationRequest input = new ThemeRecommendationRequest(
            List.of("#4EB15E", "#13730C"), "#4EB15E");

    @Test void validatesRecommendationRequest() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(input).isEmpty());
            assertFalse(validator.validate(new ThemeRecommendationRequest(List.of(), "#4EB15E")).isEmpty());
            assertFalse(validator.validate(new ThemeRecommendationRequest(List.of("invalid"), "#4EB15E")).isEmpty());
            assertFalse(validator.validate(new ThemeRecommendationRequest(List.of("#4EB15E"), null)).isEmpty());
        }
    }

    @Test void serviceRequiresPosterAndKeepsFestivalsSeparateWithoutSaving() {
        FestivalSettingRepository settings = mock(FestivalSettingRepository.class);
        ImageStorage storage = mock(ImageStorage.class);
        ThemeRecommendationClient client = mock(ThemeRecommendationClient.class);
        ThemeRecommendationValidator validator = mock(ThemeRecommendationValidator.class);
        var service = new ThemeRecommendationService(settings, storage, client, validator);
        when(settings.findById(1L)).thenReturn(Optional.of(setting(1L, null)));
        assertEquals("POSTER_NOT_FOUND", assertThrows(ThemeRecommendationException.class,
                () -> service.recommend(1L, input)).getCode());
        assertEquals("FESTIVAL_SETTING_NOT_FOUND", assertThrows(ThemeRecommendationException.class,
                () -> service.recommend(404L, input)).getCode());

        String posterA = "https://cdn/festival/1/poster/a.png";
        String posterB = "https://cdn/festival/2/poster/b.png";
        when(settings.findById(1L)).thenReturn(Optional.of(setting(1L, posterA)));
        when(settings.findById(2L)).thenReturn(Optional.of(setting(2L, posterB)));
        when(storage.readPoster(1L, posterA)).thenReturn(png);
        when(storage.readPoster(2L, posterB)).thenReturn(new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
        when(client.recommend(any(), any(), anyString())).thenReturn(theme());
        assertEquals(theme(), service.recommend(1L, input));
        assertEquals(theme(), service.recommend(2L, input));
        verify(storage).readPoster(1L, posterA);
        verify(storage).readPoster(2L, posterB);
        verify(client).recommend(png, input.sourceColors(), input.selectedBaseColor());
        verify(validator, times(2)).validate(theme());
        verify(settings, never()).save(any());
    }

    @Test void validatesHexHueCategoryAndBrandDistinction() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            var validator = new ThemeRecommendationValidator(factory.getValidator());
            assertDoesNotThrow(() -> validator.validate(theme()));
            assertEquals("AI_THEME_VALIDATION_FAILED", assertThrows(ThemeRecommendationException.class,
                    () -> validator.validate(new ThemeData(new ThemeData.Brand("bad", "#F5E3A3", "#EC4899", "#E13600"),
                            theme().status(), theme().category()))).getCode());
            assertThrows(ThemeRecommendationException.class, () -> validator.validate(new ThemeData(
                    theme().brand(), new ThemeData.Status("#4488FF", "#B26A00", "#B3261E", "#E3776A"), theme().category())));
            assertThrows(ThemeRecommendationException.class, () -> validator.validate(new ThemeData(
                    theme().brand(), theme().status(), new ThemeData.Category("#FF642D", "#FF642D", "#10B981",
                    "#F6CA3B", "#8B5CF6", "#3B82F6"))));
            assertThrows(ThemeRecommendationException.class, () -> validator.validate(new ThemeData(
                    new ThemeData.Brand("#FF642D", "#F5E3A3", "#FF642D", "#FF642D"),
                    theme().status(), theme().category())));
        }
    }

    @Test void missingKeyDoesNotCallOpenAi() {
        var client = new OpenAiThemeRecommendationClient(mapper, "", "gpt-4o-mini", "https://api.openai.com/v1/responses");
        assertEquals("OPENAI_KEY_MISSING", assertThrows(ThemeRecommendationException.class,
                () -> client.recommend(png, input.sourceColors(), input.selectedBaseColor())).getCode());
    }

    @Test void sendsImageBytesAndStrictFourteenColorSchema() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200).setBody(completed(mapper.writeValueAsString(theme()))));
            var client = new OpenAiThemeRecommendationClient(mapper, "test-secret", "gpt-4o-mini",
                    server.url("/v1/responses").toString());
            assertEquals(theme(), client.recommend(png, input.sourceColors(), input.selectedBaseColor()));
            var request = server.takeRequest();
            assertEquals("Bearer test-secret", request.getHeader("Authorization"));
            JsonNode body = mapper.readTree(request.getBody().readUtf8());
            assertFalse(body.path("store").asBoolean());
            assertEquals("gpt-4o-mini", body.path("model").asText());
            assertTrue(body.at("/input/1/content/1/image_url").asText().startsWith("data:image/png;base64,"));
            assertTrue(body.at("/input/1/content/0/text").asText().contains("#4EB15E"));
            assertEquals("json_schema", body.at("/text/format/type").asText());
            assertTrue(body.at("/text/format/strict").asBoolean());
            assertEquals(4, body.at("/text/format/schema/properties/brand/properties").size());
            assertEquals(4, body.at("/text/format/schema/properties/status/properties").size());
            assertEquals(6, body.at("/text/format/schema/properties/category/properties").size());
        }
    }

    @Test void separatesRateLimitModelAndMalformedOutput() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            var client = new OpenAiThemeRecommendationClient(mapper, "test-secret", "gpt-4o-mini",
                    server.url("/v1/responses").toString());
            server.enqueue(new MockResponse().setResponseCode(429).setBody("rate limit"));
            assertEquals("OPENAI_RATE_LIMIT", assertThrows(ThemeRecommendationException.class,
                    () -> client.recommend(png, input.sourceColors(), input.selectedBaseColor())).getCode());
            server.enqueue(new MockResponse().setResponseCode(400).setBody("model error"));
            assertEquals("OPENAI_MODEL_ERROR", assertThrows(ThemeRecommendationException.class,
                    () -> client.recommend(png, input.sourceColors(), input.selectedBaseColor())).getCode());
            server.enqueue(new MockResponse().setResponseCode(200).setBody(completed("not json")));
            assertEquals("OPENAI_MALFORMED_OUTPUT", assertThrows(ThemeRecommendationException.class,
                    () -> client.recommend(png, input.sourceColors(), input.selectedBaseColor())).getCode());
            server.enqueue(new MockResponse().setResponseCode(200).setBody("not an envelope"));
            assertEquals("OPENAI_MALFORMED_OUTPUT", assertThrows(ThemeRecommendationException.class,
                    () -> client.recommend(png, input.sourceColors(), input.selectedBaseColor())).getCode());
            server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":\"incomplete\"}"));
            assertEquals("OPENAI_MODEL_ERROR", assertThrows(ThemeRecommendationException.class,
                    () -> client.recommend(png, input.sourceColors(), input.selectedBaseColor())).getCode());
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, assertThrows(ThemeRecommendationException.class,
                    () -> client.recommend(new byte[]{1, 2, 3}, input.sourceColors(), input.selectedBaseColor())).getStatus());
        }
    }

    private String completed(String themeJson) throws Exception {
        return mapper.writeValueAsString(Map.of("status", "completed", "output", List.of(
                Map.of("content", List.of(Map.of("type", "output_text", "text", themeJson))))));
    }

    private ThemeData theme() {
        return new ThemeData(new ThemeData.Brand("#FF642D", "#F5E3A3", "#EC4899", "#E13600"),
                new ThemeData.Status("#2E7D32", "#B26A00", "#B3261E", "#E3776A"),
                new ThemeData.Category("#FF642D", "#EC4899", "#10B981", "#F6CA3B", "#8B5CF6", "#3B82F6"));
    }

    private FestivalSetting setting(Long id, String poster) {
        FestivalSetting setting = FestivalSetting.create("School", "Festival", LocalDate.now(), LocalDate.now(), false);
        ReflectionTestUtils.setField(setting, "id", id);
        if (poster != null) setting.updatePosterImageUrl(poster);
        return setting;
    }
}
