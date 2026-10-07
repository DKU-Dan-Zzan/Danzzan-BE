package com.danzzan.domain.festival.theme;

import com.danzzan.domain.festival.entity.FestivalSetting;
import com.danzzan.domain.festival.repository.FestivalSettingRepository;
import com.danzzan.domain.festival.theme.dto.ThemeData;
import com.danzzan.domain.festival.theme.dto.ThemeRecommendationRequest;
import com.danzzan.domain.festival.theme.dto.ThemeRecommendationCandidate;
import com.danzzan.domain.festival.theme.dto.ThemeRecommendationResponse;
import com.danzzan.domain.festival.theme.controller.AdminThemeController;
import com.danzzan.domain.festival.theme.exception.ThemeRecommendationException;
import com.danzzan.domain.festival.theme.service.OpenAiThemeRecommendationClient;
import com.danzzan.domain.festival.theme.service.ThemeRecommendationClient;
import com.danzzan.domain.festival.theme.service.ThemeRecommendationService;
import com.danzzan.domain.festival.theme.service.ThemeRecommendationValidator;
import com.danzzan.domain.festival.theme.service.ThemeService;
import com.danzzan.infra.image.ImageStorage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        var service = new ThemeRecommendationService(settings, storage, client, validator, mock(Environment.class));
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
        when(client.recommend(any(), any(), anyString())).thenReturn(response());
        assertEquals(response(), service.recommend(1L, input));
        assertEquals(response(), service.recommend(2L, input));
        verify(storage).readPoster(1L, posterA);
        verify(storage).readPoster(2L, posterB);
        verify(client).recommend(png, input.sourceColors(), input.selectedBaseColor());
        verify(validator, times(2)).diagnose(theme());
        verify(validator, times(2)).diagnose(vibrant());
        verify(validator, times(2)).diagnose(deep());
        verify(settings, never()).save(any());
    }

    @Test void rejectsWrongCountDuplicateAndIndistinguishableCandidates() {
        FestivalSettingRepository settings = mock(FestivalSettingRepository.class);
        ImageStorage storage = mock(ImageStorage.class);
        ThemeRecommendationClient client = mock(ThemeRecommendationClient.class);
        ThemeRecommendationValidator validator = mock(ThemeRecommendationValidator.class);
        var service = new ThemeRecommendationService(settings, storage, client, validator, mock(Environment.class));
        String poster = "poster.png";
        when(settings.findById(1L)).thenReturn(Optional.of(setting(1L, poster)));
        when(storage.readPoster(1L, poster)).thenReturn(png);
        when(client.recommend(any(), any(), anyString())).thenReturn(new ThemeRecommendationResponse(response().candidates().subList(0, 2)));
        assertEquals("AI_THEME_CANDIDATE_COUNT", assertThrows(ThemeRecommendationException.class,
                () -> service.recommend(1L, input)).getCode());
        when(client.recommend(any(), any(), anyString())).thenReturn(new ThemeRecommendationResponse(List.of(
                response().candidates().get(0), response().candidates().get(1), response().candidates().get(1))));
        assertEquals("AI_THEME_DUPLICATE_CANDIDATE", assertThrows(ThemeRecommendationException.class,
                () -> service.recommend(1L, input)).getCode());
        when(client.recommend(any(), any(), anyString())).thenReturn(new ThemeRecommendationResponse(List.of(
                candidate("balanced", theme()), candidate("vibrant", theme()), candidate("deep", theme()))));
        assertEquals("AI_THEME_VALIDATION_FAILED", assertThrows(ThemeRecommendationException.class,
                () -> service.recommend(1L, input)).getCode());
        ThemeData near = new ThemeData(new ThemeData.Brand("#FF652D", "#F5E3A3", "#EC4899", "#E13600"),
                theme().status(), theme().category());
        when(client.recommend(any(), any(), anyString())).thenReturn(new ThemeRecommendationResponse(List.of(
                candidate("balanced", theme()), candidate("vibrant", near), candidate("deep", deep()))));
        assertEquals("AI_THEME_VALIDATION_FAILED", assertThrows(ThemeRecommendationException.class,
                () -> service.recommend(1L, input)).getCode());
        when(client.recommend(any(), any(), anyString())).thenReturn(new ThemeRecommendationResponse(List.of(
                response().candidates().get(0), response().candidates().get(1), response().candidates().get(2),
                candidate("extra", theme()))));
        assertEquals("AI_THEME_CANDIDATE_COUNT", assertThrows(ThemeRecommendationException.class,
                () -> service.recommend(1L, input)).getCode());
    }

    @Test void appliesExistingValidatorToEveryCandidate() {
        FestivalSettingRepository settings = mock(FestivalSettingRepository.class);
        ImageStorage storage = mock(ImageStorage.class);
        ThemeRecommendationClient client = mock(ThemeRecommendationClient.class);
        ThemeRecommendationValidator validator = mock(ThemeRecommendationValidator.class);
        var service = new ThemeRecommendationService(settings, storage, client, validator, mock(Environment.class));
        when(settings.findById(1L)).thenReturn(Optional.of(setting(1L, "poster.png")));
        when(storage.readPoster(1L, "poster.png")).thenReturn(png);
        when(client.recommend(any(), any(), anyString())).thenReturn(response());
        when(validator.diagnose(theme())).thenReturn(List.of(new ThemeRecommendationValidator.Failure(
                "STATUS_WARNING_HUE_OUT_OF_RANGE", Map.of("warning", "#4488FF"), Map.of("hue", 210.0), "hue=20.0..65.0")));
        when(validator.diagnose(deep())).thenReturn(List.of(new ThemeRecommendationValidator.Failure(
                "CATEGORY_DISTANCE_TOO_SMALL", Map.of("pub", "#FF642D", "foodTruck", "#FF642D"),
                Map.of("distance", 0.0), "distance>=35")));
        assertEquals("AI_THEME_VALIDATION_FAILED", assertThrows(ThemeRecommendationException.class,
                () -> service.recommend(1L, input)).getCode());
        verify(validator).diagnose(theme());
        verify(validator).diagnose(vibrant());
        verify(validator).diagnose(deep());
    }

    @Test void returnsOnlyTwoValidCandidatesWhenCategoryColorsRepeat() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            FestivalSettingRepository settings = mock(FestivalSettingRepository.class);
            ImageStorage storage = mock(ImageStorage.class);
            ThemeRecommendationClient client = mock(ThemeRecommendationClient.class);
            var service = new ThemeRecommendationService(settings, storage, client,
                    new ThemeRecommendationValidator(factory.getValidator()), mock(Environment.class));
            when(settings.findById(1L)).thenReturn(Optional.of(setting(1L, "poster.png")));
            when(storage.readPoster(1L, "poster.png")).thenReturn(png);
            when(client.recommend(any(), any(), anyString())).thenReturn(new ThemeRecommendationResponse(List.of(
                    candidate("balanced", duplicateCategory(theme())),
                    candidate("vibrant", vibrant()), candidate("deep", deep()))));

            ThemeRecommendationResponse result = service.recommend(1L, input);
            assertEquals(List.of("vibrant", "deep"), result.candidates().stream().map(ThemeRecommendationCandidate::id).toList());
            verify(client, times(1)).recommend(png, input.sourceColors(), input.selectedBaseColor());
            assertTrue(new ThemeRecommendationValidator(factory.getValidator()).diagnose(duplicateCategory(theme()))
                    .stream().anyMatch(failure -> failure.rule().equals("CATEGORY_DISTANCE_TOO_SMALL")
                            && failure.measured().get("distance") == 0.0
                            && failure.threshold().equals("distance>=35")));

            when(client.recommend(any(), any(), anyString())).thenReturn(new ThemeRecommendationResponse(List.of(
                    candidate("balanced", duplicateCategory(theme())), candidate("vibrant", vibrant()),
                    candidate("deep", duplicateCategory(deep())))));
            assertEquals("AI_THEME_VALIDATION_FAILED", assertThrows(ThemeRecommendationException.class,
                    () -> service.recommend(1L, input)).getCode());

            when(client.recommend(any(), any(), anyString())).thenReturn(new ThemeRecommendationResponse(List.of(
                    candidate("balanced", duplicateCategory(theme())),
                    candidate("vibrant", duplicateCategory(vibrant())),
                    candidate("deep", duplicateCategory(deep())))));
            assertEquals("AI_THEME_VALIDATION_FAILED", assertThrows(ThemeRecommendationException.class,
                    () -> service.recommend(1L, input)).getCode());
        }
    }

    @Test void twoCandidateRecommendationIsAnHttp200Response() throws Exception {
        ThemeRecommendationService recommendationService = mock(ThemeRecommendationService.class);
        when(recommendationService.recommend(eq(1L), any())).thenReturn(new ThemeRecommendationResponse(List.of(
                candidate("vibrant", vibrant()), candidate("deep", deep()))));
        var mvc = MockMvcBuilders.standaloneSetup(new AdminThemeController(
                mock(ThemeService.class), recommendationService)).build();
        mvc.perform(post("/admin/festival/1/themes/recommend")
                        .contentType("application/json").content(mapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates.length()").value(2))
                .andExpect(jsonPath("$.candidates[0].id").value("vibrant"))
                .andExpect(jsonPath("$.candidates[1].id").value("deep"));
    }

    @Test void checksDiversityBetweenTheTwoSurvivingCandidates() {
        FestivalSettingRepository settings = mock(FestivalSettingRepository.class);
        ImageStorage storage = mock(ImageStorage.class);
        ThemeRecommendationClient client = mock(ThemeRecommendationClient.class);
        when(settings.findById(1L)).thenReturn(Optional.of(setting(1L, "poster.png")));
        when(storage.readPoster(1L, "poster.png")).thenReturn(png);
        ThemeData near = new ThemeData(new ThemeData.Brand("#FF652D", "#F5E3A3", "#EC4899", "#E13600"),
                theme().status(), theme().category());
        when(client.recommend(any(), any(), anyString())).thenReturn(new ThemeRecommendationResponse(List.of(
                candidate("balanced", theme()), candidate("vibrant", near), candidate("deep", deep()))));
        // The third candidate is rejected individually; the remaining pair still fails existing diversity rules.
        ThemeRecommendationValidator validator = mock(ThemeRecommendationValidator.class);
        var filteredService = new ThemeRecommendationService(settings, storage, client, validator, mock(Environment.class));
        when(validator.diagnose(deep())).thenReturn(List.of(new ThemeRecommendationValidator.Failure(
                "CATEGORY_DISTANCE_TOO_SMALL", Map.of("pub", "#FF642D", "restArea", "#FF642D"),
                Map.of("distance", 0.0), "distance>=35")));
        assertEquals("AI_THEME_VALIDATION_FAILED", assertThrows(ThemeRecommendationException.class,
                () -> filteredService.recommend(1L, input)).getCode());
    }

    @Test void validatesHexHueCategoryAndBrandDistinction() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            var validator = new ThemeRecommendationValidator(factory.getValidator());
            assertDoesNotThrow(() -> validator.validate(theme()));
            assertDoesNotThrow(() -> validator.validate(vibrant()));
            assertDoesNotThrow(() -> validator.validate(deep()));
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

    @Test void diagnosesEveryRuleWithColorsMeasurementsAndThresholds() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            var validator = new ThemeRecommendationValidator(factory.getValidator());
            ThemeData invalid = new ThemeData(
                    new ThemeData.Brand("#FF642D", "#F5E3A3", "#FF642D", "#FF642D"),
                    new ThemeData.Status("#4488FF", "#4488FF", "#B3261E", "#E3776A"),
                    new ThemeData.Category("#FF642D", "#FF642D", "#10B981", "#F6CA3B", "#8B5CF6", "#3B82F6"));
            var failures = validator.diagnose(invalid);
            assertTrue(failures.stream().anyMatch(f -> f.rule().equals("STATUS_SUCCESS_HUE_OUT_OF_RANGE")
                    && f.colors().get("success").equals("#4488FF") && f.measured().containsKey("hue")
                    && f.threshold().contains("75.0..175.0")));
            assertTrue(failures.stream().anyMatch(f -> f.rule().equals("STATUS_WARNING_HUE_OUT_OF_RANGE")
                    && f.threshold().contains("20.0..65.0")));
            assertTrue(failures.stream().anyMatch(f -> f.rule().equals("CATEGORY_DISTANCE_TOO_SMALL")
                    && f.measured().get("distance") == 0.0 && f.threshold().equals("distance>=35")));
            assertTrue(failures.stream().anyMatch(f -> f.rule().equals("BRAND_PRIMARY_NOT_DISTINCT")
                    && f.measured().get("primaryAccentDistance") == 0.0
                    && f.measured().get("primaryStrongDistance") == 0.0));
            assertTrue(validator.diagnose(new ThemeData(new ThemeData.Brand("not-a-hex", "#F5E3A3", "#EC4899", "#E13600"),
                    theme().status(), theme().category())).stream().anyMatch(f -> f.rule().equals("HEX_OR_REQUIRED_FIELD")
                    && f.colors().containsValue("<invalid color>")));
        }
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void logsCandidateDiagnosticsOnlyForLocalProfile(CapturedOutput output) {
        FestivalSettingRepository settings = mock(FestivalSettingRepository.class);
        ImageStorage storage = mock(ImageStorage.class);
        ThemeRecommendationClient client = mock(ThemeRecommendationClient.class);
        ThemeRecommendationValidator validator = mock(ThemeRecommendationValidator.class);
        when(settings.findById(1L)).thenReturn(Optional.of(setting(1L, "poster.png")));
        when(storage.readPoster(1L, "poster.png")).thenReturn(png);
        when(client.recommend(any(), any(), anyString())).thenReturn(response());
        when(validator.diagnose(deep())).thenReturn(List.of(new ThemeRecommendationValidator.Failure(
                "CATEGORY_DISTANCE_TOO_SMALL", Map.of("pub", "#FF642D", "foodTruck", "#FF642D"),
                Map.of("distance", 0.0), "distance>=35")));
        MockEnvironment local = new MockEnvironment();
        local.setActiveProfiles("dev");
        var localService = new ThemeRecommendationService(settings, storage, client, validator, local);
        assertEquals(List.of("balanced", "vibrant"), localService.recommend(1L, input).candidates().stream()
                .map(ThemeRecommendationCandidate::id).toList());
        assertTrue(output.getOut().contains("candidate=balanced name=balanced result=PASS"));
        assertTrue(output.getOut().contains("candidate=vibrant name=vibrant result=PASS"));
        assertTrue(output.getOut().contains("candidate=deep name=deep result=FAIL"));
        assertTrue(output.getOut().contains("candidate=deep name=deep rule=CATEGORY_DISTANCE_TOO_SMALL"));
        assertTrue(output.getOut().contains("distance=0.0"));
        assertTrue(output.getOut().contains("threshold=distance>=35"));

        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod", "dev");
        var prodService = new ThemeRecommendationService(settings, storage, client, validator, prod);
        int before = output.getOut().length();
        assertEquals(2, prodService.recommend(1L, input).candidates().size());
        assertFalse(output.getOut().substring(before).contains("AI theme diagnostic"));
        assertFalse(output.getOut().substring(before).contains("AI theme validation candidate="));

        when(validator.diagnose(deep())).thenReturn(List.of());
        when(client.recommend(any(), any(), anyString())).thenReturn(new ThemeRecommendationResponse(List.of(
                candidate("balanced", theme()), candidate("vibrant", theme()), candidate("deep", theme()))));
        assertEquals("AI_THEME_VALIDATION_FAILED", assertThrows(ThemeRecommendationException.class,
                () -> localService.recommend(1L, input)).getCode());
        assertTrue(output.getOut().contains("rule=CANDIDATE_PRIMARY_DUPLICATE"));

        ThemeData near = new ThemeData(new ThemeData.Brand("#FF652D", "#F5E3A3", "#EC4899", "#E13600"),
                theme().status(), theme().category());
        when(client.recommend(any(), any(), anyString())).thenReturn(new ThemeRecommendationResponse(List.of(
                candidate("balanced", theme()), candidate("vibrant", near), candidate("deep", deep()))));
        assertEquals("AI_THEME_VALIDATION_FAILED", assertThrows(ThemeRecommendationException.class,
                () -> localService.recommend(1L, input)).getCode());
        assertTrue(output.getOut().contains("rule=CANDIDATE_BRAND_TOO_SIMILAR"));
        assertTrue(output.getOut().contains("threshold=averageRgbDistance>=30"));
    }

    @Test void missingKeyDoesNotCallOpenAi() {
        var client = new OpenAiThemeRecommendationClient(mapper, "", "gpt-4o-mini", "https://api.openai.com/v1/responses");
        assertEquals("OPENAI_KEY_MISSING", assertThrows(ThemeRecommendationException.class,
                () -> client.recommend(png, input.sourceColors(), input.selectedBaseColor())).getCode());
    }

    @Test void sendsImageBytesAndStrictFourteenColorSchema() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200).setBody(completed(mapper.writeValueAsString(response()))));
            var client = new OpenAiThemeRecommendationClient(mapper, "test-secret", "gpt-4o-mini",
                    server.url("/v1/responses").toString());
            assertEquals(response(), client.recommend(png, input.sourceColors(), input.selectedBaseColor()));
            var request = server.takeRequest();
            assertEquals("Bearer test-secret", request.getHeader("Authorization"));
            JsonNode body = mapper.readTree(request.getBody().readUtf8());
            assertFalse(body.path("store").asBoolean());
            assertEquals("gpt-4o-mini", body.path("model").asText());
            assertTrue(body.at("/input/1/content/1/image_url").asText().startsWith("data:image/png;base64,"));
            assertTrue(body.at("/input/1/content/0/text").asText().contains("#4EB15E"));
            String instructions = body.at("/input/0/content").asText();
            assertTrue(instructions.contains("three related variations of one poster-derived theme"));
            assertTrue(instructions.contains("Balanced (name it Original or Balanced)"));
            assertTrue(instructions.contains("Vibrant (name it Light)"));
            assertTrue(instructions.contains("Deep: keep Balanced's hue and palette family"));
            assertTrue(instructions.contains("mainly by lightness, saturation and depth"));
            assertTrue(instructions.contains("prioritize (1) poster-derived palette harmony"));
            assertTrue(instructions.contains("nearby hue, lightness and"));
            assertTrue(instructions.contains("distinct HEX values"));
            assertTrue(instructions.contains("RGB Euclidean distance at least 35 for every pair"));
            assertEquals("json_schema", body.at("/text/format/type").asText());
            assertTrue(body.at("/text/format/strict").asBoolean());
            assertEquals(3, body.at("/text/format/schema/properties/candidates/minItems").asInt());
            assertEquals(3, body.at("/text/format/schema/properties/candidates/maxItems").asInt());
            assertEquals(4, body.at("/text/format/schema/properties/candidates/items/properties/themeData/properties/brand/properties").size());
            assertEquals(4, body.at("/text/format/schema/properties/candidates/items/properties/themeData/properties/status/properties").size());
            assertEquals(6, body.at("/text/format/schema/properties/candidates/items/properties/themeData/properties/category/properties").size());
            assertFalse(body.at("/text/format/schema/properties/candidates/items/additionalProperties").asBoolean());
            assertEquals(1, server.getRequestCount());
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

    private ThemeData vibrant() {
        return new ThemeData(new ThemeData.Brand("#F69324", "#F5D251", "#B74FB4", "#A62D00"),
                theme().status(), theme().category());
    }

    private ThemeData deep() {
        return new ThemeData(new ThemeData.Brand("#983508", "#DBB477", "#C04473", "#711D08"),
                theme().status(), theme().category());
    }

    private ThemeData duplicateCategory(ThemeData data) {
        var category = data.category();
        return new ThemeData(data.brand(), data.status(), new ThemeData.Category(category.pub(),
                category.foodTruck(), category.experience(), category.event(), category.pub(), category.facility()));
    }

    private ThemeRecommendationCandidate candidate(String id, ThemeData data) {
        return new ThemeRecommendationCandidate(id, id, "Short candidate description", data);
    }

    private ThemeRecommendationResponse response() {
        return new ThemeRecommendationResponse(List.of(candidate("balanced", theme()),
                candidate("vibrant", vibrant()), candidate("deep", deep())));
    }

    private FestivalSetting setting(Long id, String poster) {
        FestivalSetting setting = FestivalSetting.create("School", "Festival", LocalDate.now(), LocalDate.now(), false);
        ReflectionTestUtils.setField(setting, "id", id);
        if (poster != null) setting.updatePosterImageUrl(poster);
        return setting;
    }
}
