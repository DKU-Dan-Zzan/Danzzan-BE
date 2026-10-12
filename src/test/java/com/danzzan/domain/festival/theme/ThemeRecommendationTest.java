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

    @Test void rejectsWrongCountAndDuplicateButFiltersIndistinguishableCandidates() {
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
        assertEquals(List.of("balanced"), service.recommend(1L, input).candidates().stream()
                .map(ThemeRecommendationCandidate::id).toList());
        ThemeData near = new ThemeData(new ThemeData.Brand("#FF652D", "#F5E3A3", "#EC4899", "#E13600"),
                theme().status(), theme().category());
        when(client.recommend(any(), any(), anyString())).thenReturn(new ThemeRecommendationResponse(List.of(
                candidate("balanced", theme()), candidate("vibrant", near), candidate("deep", deep()))));
        assertEquals(List.of("balanced", "deep"), service.recommend(1L, input).candidates().stream()
                .map(ThemeRecommendationCandidate::id).toList());
        when(client.recommend(any(), any(), anyString())).thenReturn(new ThemeRecommendationResponse(List.of(
                response().candidates().get(0), response().candidates().get(1), response().candidates().get(2),
                candidate("extra", theme()))));
        assertEquals("AI_THEME_CANDIDATE_COUNT", assertThrows(ThemeRecommendationException.class,
                () -> service.recommend(1L, input)).getCode());
    }

    @Test void keepsValidBalancedFirstEvenWhenModelReturnsItLast() {
        FestivalSettingRepository settings = mock(FestivalSettingRepository.class);
        ImageStorage storage = mock(ImageStorage.class);
        ThemeRecommendationClient client = mock(ThemeRecommendationClient.class);
        ThemeRecommendationValidator validator = mock(ThemeRecommendationValidator.class);
        var service = new ThemeRecommendationService(settings, storage, client, validator, mock(Environment.class));
        when(settings.findById(1L)).thenReturn(Optional.of(setting(1L, "poster.png")));
        when(storage.readPoster(1L, "poster.png")).thenReturn(png);
        when(client.recommend(any(), any(), anyString())).thenReturn(new ThemeRecommendationResponse(List.of(
                candidate("vibrant", vibrant()), candidate("deep", deep()), candidate("balanced", theme()))));
        assertEquals(List.of("balanced", "vibrant", "deep"), service.recommend(1L, input).candidates().stream()
                .map(ThemeRecommendationCandidate::id).toList());

        when(client.recommend(any(), any(), anyString())).thenReturn(new ThemeRecommendationResponse(List.of(
                candidate("vibrant", theme()), candidate("deep", theme()), candidate("balanced", theme()))));
        assertEquals(List.of("balanced"), service.recommend(1L, input).candidates().stream()
                .map(ThemeRecommendationCandidate::id).toList());
        verify(client, times(2)).recommend(png, input.sourceColors(), input.selectedBaseColor());
    }

    @Test void returnsBalancedWhenBothSecondaryVariationsFailValidation() {
        FestivalSettingRepository settings = mock(FestivalSettingRepository.class);
        ImageStorage storage = mock(ImageStorage.class);
        ThemeRecommendationClient client = mock(ThemeRecommendationClient.class);
        ThemeRecommendationValidator validator = mock(ThemeRecommendationValidator.class);
        var service = new ThemeRecommendationService(settings, storage, client, validator, mock(Environment.class));
        when(settings.findById(1L)).thenReturn(Optional.of(setting(1L, "poster.png")));
        when(storage.readPoster(1L, "poster.png")).thenReturn(png);
        when(client.recommend(any(), any(), anyString())).thenReturn(response());
        var categoryFailure = new ThemeRecommendationValidator.Failure("CATEGORY_DISTANCE_TOO_SMALL",
                Map.of("pub", "#FF642D", "restArea", "#FF642D"), Map.of("distance", 0.0), "distance>=35");
        when(validator.diagnose(vibrant())).thenReturn(List.of(categoryFailure));
        when(validator.diagnose(deep())).thenReturn(List.of(categoryFailure));

        assertEquals(List.of("balanced"), service.recommend(1L, input).candidates().stream()
                .map(ThemeRecommendationCandidate::id).toList());
        verify(validator).diagnose(theme());
        verify(validator).diagnose(vibrant());
        verify(validator).diagnose(deep());
        verify(client, times(1)).recommend(png, input.sourceColors(), input.selectedBaseColor());
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
        assertEquals(List.of("vibrant"), service.recommend(1L, input).candidates().stream()
                .map(ThemeRecommendationCandidate::id).toList());
        verify(client, times(1)).recommend(png, input.sourceColors(), input.selectedBaseColor());
        verify(validator).diagnose(theme());
        verify(validator).diagnose(vibrant());
        verify(validator).diagnose(deep());
    }

    @Test void repairsRepeatedCategoryColorsBeforeFilteringCandidates() {
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
            assertEquals(List.of("balanced", "vibrant", "deep"), result.candidates().stream()
                    .map(ThemeRecommendationCandidate::id).toList());
            assertTrue(new ThemeRecommendationValidator(factory.getValidator())
                    .diagnose(result.candidates().get(0).themeData()).isEmpty());
            assertEquals(theme().brand(), result.candidates().get(0).themeData().brand());
            verify(client, times(1)).recommend(png, input.sourceColors(), input.selectedBaseColor());
            assertTrue(new ThemeRecommendationValidator(factory.getValidator()).diagnose(duplicateCategory(theme()))
                    .stream().anyMatch(failure -> failure.rule().equals("CATEGORY_DISTANCE_TOO_SMALL")
                            && failure.measured().get("distance") == 0.0
                            && failure.threshold().equals("distance>=35")));

            when(client.recommend(any(), any(), anyString())).thenReturn(new ThemeRecommendationResponse(List.of(
                    candidate("balanced", duplicateCategory(theme())), candidate("vibrant", vibrant()),
                    candidate("deep", duplicateCategory(deep())))));
            assertEquals(List.of("balanced", "vibrant", "deep"), service.recommend(1L, input).candidates().stream()
                    .map(ThemeRecommendationCandidate::id).toList());

            when(client.recommend(any(), any(), anyString())).thenReturn(new ThemeRecommendationResponse(List.of(
                    candidate("balanced", duplicateCategory(theme())),
                    candidate("vibrant", duplicateCategory(vibrant())),
                    candidate("deep", duplicateCategory(deep())))));
            assertEquals(List.of("balanced", "vibrant", "deep"), service.recommend(1L, input).candidates().stream()
                    .map(ThemeRecommendationCandidate::id).toList());
            verify(settings, never()).save(any());
        }
    }

    @Test void returnsRepairedOriginalWhenVariationsHaveWrongSemanticHue() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            FestivalSettingRepository settings = mock(FestivalSettingRepository.class);
            ImageStorage storage = mock(ImageStorage.class);
            ThemeRecommendationClient client = mock(ThemeRecommendationClient.class);
            var validator = new ThemeRecommendationValidator(factory.getValidator());
            var service = new ThemeRecommendationService(settings, storage, client, validator, mock(Environment.class));
            when(settings.findById(1L)).thenReturn(Optional.of(setting(1L, "poster.png")));
            when(storage.readPoster(1L, "poster.png")).thenReturn(png);
            ThemeData original = new ThemeData(theme().brand(),
                    new ThemeData.Status("#99C799", theme().status().warning(), theme().status().danger(),
                            theme().status().pending()), duplicateCategory(theme()).category());
            ThemeData wrongHue = new ThemeData(theme().brand(),
                    new ThemeData.Status("#7BA6D8", theme().status().warning(), theme().status().danger(),
                            theme().status().pending()), theme().category());
            when(client.recommend(any(), any(), anyString())).thenReturn(new ThemeRecommendationResponse(List.of(
                    candidate("balanced", original), candidate("vibrant", wrongHue), candidate("deep", wrongHue))));

            ThemeRecommendationResponse result = service.recommend(1L, input);
            assertEquals(List.of("balanced"), result.candidates().stream().map(ThemeRecommendationCandidate::id).toList());
            ThemeData repaired = result.candidates().get(0).themeData();
            assertNotEquals(original.status().success(), repaired.status().success());
            assertNotEquals(original.category(), repaired.category());
            assertTrue(validator.diagnose(repaired).isEmpty());
            verify(client, times(1)).recommend(png, input.sourceColors(), input.selectedBaseColor());
            verify(settings, never()).save(any());
        }
    }

    @Test void rejectsUnrepairableAiColorsWithoutSavingOrActivating() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            FestivalSettingRepository settings = mock(FestivalSettingRepository.class);
            ImageStorage storage = mock(ImageStorage.class);
            ThemeRecommendationClient client = mock(ThemeRecommendationClient.class);
            var service = new ThemeRecommendationService(settings, storage, client,
                    new ThemeRecommendationValidator(factory.getValidator()), mock(Environment.class));
            when(settings.findById(1L)).thenReturn(Optional.of(setting(1L, "poster.png")));
            when(storage.readPoster(1L, "poster.png")).thenReturn(png);
            ThemeData.Category malformed = new ThemeData.Category("invalid", "#EC4899", "#10B981",
                    "#F6CA3B", "#8B5CF6", "#3B82F6");
            when(client.recommend(any(), any(), anyString())).thenReturn(new ThemeRecommendationResponse(List.of(
                    candidate("balanced", new ThemeData(theme().brand(), theme().status(), malformed)),
                    candidate("vibrant", new ThemeData(vibrant().brand(), theme().status(), malformed)),
                    candidate("deep", new ThemeData(deep().brand(), theme().status(), malformed)))));

            assertEquals("AI_THEME_VALIDATION_FAILED", assertThrows(ThemeRecommendationException.class,
                    () -> service.recommend(1L, input)).getCode());
            verify(client, times(1)).recommend(png, input.sourceColors(), input.selectedBaseColor());
            verify(settings, never()).save(any());
        }
    }

    @Test void oneCandidateRecommendationIsAnHttp200Response() throws Exception {
        ThemeRecommendationService recommendationService = mock(ThemeRecommendationService.class);
        when(recommendationService.recommend(eq(1L), any())).thenReturn(new ThemeRecommendationResponse(List.of(
                candidate("vibrant", vibrant()))));
        var mvc = MockMvcBuilders.standaloneSetup(new AdminThemeController(
                mock(ThemeService.class), recommendationService)).build();
        mvc.perform(post("/admin/festival/1/themes/recommend")
                        .contentType("application/json").content(mapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates.length()").value(1))
                .andExpect(jsonPath("$.candidates[0].id").value("vibrant"));
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

    @Test void keepsOneCandidateWhenTheTwoSurvivorsAreTooSimilar() {
        FestivalSettingRepository settings = mock(FestivalSettingRepository.class);
        ImageStorage storage = mock(ImageStorage.class);
        ThemeRecommendationClient client = mock(ThemeRecommendationClient.class);
        when(settings.findById(1L)).thenReturn(Optional.of(setting(1L, "poster.png")));
        when(storage.readPoster(1L, "poster.png")).thenReturn(png);
        ThemeData near = new ThemeData(new ThemeData.Brand("#FF652D", "#F5E3A3", "#EC4899", "#E13600"),
                theme().status(), theme().category());
        when(client.recommend(any(), any(), anyString())).thenReturn(new ThemeRecommendationResponse(List.of(
                candidate("balanced", theme()), candidate("vibrant", near), candidate("deep", deep()))));
        // The third candidate is rejected individually; the similar second candidate is omitted.
        ThemeRecommendationValidator validator = mock(ThemeRecommendationValidator.class);
        var filteredService = new ThemeRecommendationService(settings, storage, client, validator, mock(Environment.class));
        when(validator.diagnose(deep())).thenReturn(List.of(new ThemeRecommendationValidator.Failure(
                "CATEGORY_DISTANCE_TOO_SMALL", Map.of("pub", "#FF642D", "restArea", "#FF642D"),
                Map.of("distance", 0.0), "distance>=35")));
        assertEquals(List.of("balanced"), filteredService.recommend(1L, input).candidates().stream()
                .map(ThemeRecommendationCandidate::id).toList());
    }

    @Test void acceptsPerceptibleLightAndDeepVariationsWithinTheSameHueFamily() {
        FestivalSettingRepository settings = mock(FestivalSettingRepository.class);
        ImageStorage storage = mock(ImageStorage.class);
        ThemeRecommendationClient client = mock(ThemeRecommendationClient.class);
        ThemeRecommendationValidator validator = mock(ThemeRecommendationValidator.class);
        var service = new ThemeRecommendationService(settings, storage, client, validator, mock(Environment.class));
        when(settings.findById(1L)).thenReturn(Optional.of(setting(1L, "poster.png")));
        when(storage.readPoster(1L, "poster.png")).thenReturn(png);
        ThemeData light = new ThemeData(new ThemeData.Brand("#FF814F", "#F5E3A3", "#EC4899", "#F46B38"),
                theme().status(), theme().category());
        ThemeData dark = new ThemeData(new ThemeData.Brand("#D44E21", "#F5E3A3", "#EC4899", "#AD2A00"),
                theme().status(), theme().category());
        when(client.recommend(any(), any(), anyString())).thenReturn(new ThemeRecommendationResponse(List.of(
                candidate("deep", dark), candidate("vibrant", light), candidate("balanced", theme()))));

        assertEquals(List.of("balanced", "vibrant", "deep"), service.recommend(1L, input).candidates().stream()
                .map(ThemeRecommendationCandidate::id).toList());
        verify(validator).diagnose(theme());
        verify(validator).diagnose(light);
        verify(validator).diagnose(dark);
    }

    @Test void allowsUnchangedPrimaryWhenStrongHasAMeaningfulDirectionalShift() {
        FestivalSettingRepository settings = mock(FestivalSettingRepository.class);
        ImageStorage storage = mock(ImageStorage.class);
        ThemeRecommendationClient client = mock(ThemeRecommendationClient.class);
        var service = new ThemeRecommendationService(settings, storage, client,
                mock(ThemeRecommendationValidator.class), mock(Environment.class));
        when(settings.findById(1L)).thenReturn(Optional.of(setting(1L, "poster.png")));
        when(storage.readPoster(1L, "poster.png")).thenReturn(png);
        ThemeData light = new ThemeData(new ThemeData.Brand("#FF642D", "#F5E3A3", "#EC4899", "#FF8450"),
                theme().status(), theme().category());
        ThemeData dark = new ThemeData(new ThemeData.Brand("#FF642D", "#F5E3A3", "#EC4899", "#922000"),
                theme().status(), theme().category());
        when(client.recommend(any(), any(), anyString())).thenReturn(new ThemeRecommendationResponse(List.of(
                candidate("balanced", theme()), candidate("vibrant", light), candidate("deep", dark))));

        assertEquals(List.of("balanced", "vibrant", "deep"), service.recommend(1L, input).candidates().stream()
                .map(ThemeRecommendationCandidate::id).toList());
    }

    @Test void rejectsOppositeDirectionAndNearDuplicateWithoutRemovingOriginal() {
        FestivalSettingRepository settings = mock(FestivalSettingRepository.class);
        ImageStorage storage = mock(ImageStorage.class);
        ThemeRecommendationClient client = mock(ThemeRecommendationClient.class);
        var service = new ThemeRecommendationService(settings, storage, client,
                mock(ThemeRecommendationValidator.class), mock(Environment.class));
        when(settings.findById(1L)).thenReturn(Optional.of(setting(1L, "poster.png")));
        when(storage.readPoster(1L, "poster.png")).thenReturn(png);
        ThemeData near = new ThemeData(new ThemeData.Brand("#FF652D", "#F5E3A3", "#EC4899", "#E23700"),
                theme().status(), theme().category());
        when(client.recommend(any(), any(), anyString())).thenReturn(new ThemeRecommendationResponse(List.of(
                candidate("balanced", theme()), candidate("vibrant", deep()), candidate("deep", vibrant()))));
        assertEquals(List.of("balanced"), service.recommend(1L, input).candidates().stream()
                .map(ThemeRecommendationCandidate::id).toList());
        when(client.recommend(any(), any(), anyString())).thenReturn(new ThemeRecommendationResponse(List.of(
                candidate("balanced", theme()), candidate("vibrant", near), candidate("deep", deep()))));
        assertEquals(List.of("balanced", "deep"), service.recommend(1L, input).candidates().stream()
                .map(ThemeRecommendationCandidate::id).toList());
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
        assertTrue(output.getOut().contains("candidate=deep stage=PALETTE_ADJUSTMENT reason=CATEGORY_DISTANCE_UNRESOLVED"));
        assertTrue(output.getOut().contains("candidate=deep stage=CANDIDATE_VALIDATION"));
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
        assertEquals(List.of("balanced"), localService.recommend(1L, input).candidates().stream()
                .map(ThemeRecommendationCandidate::id).toList());
        assertTrue(output.getOut().contains("stage=DIVERSITY rule=CANDIDATE_VARIATION_TOO_SUBTLE"));

        ThemeData near = new ThemeData(new ThemeData.Brand("#FF652D", "#F5E3A3", "#EC4899", "#E13600"),
                theme().status(), theme().category());
        when(client.recommend(any(), any(), anyString())).thenReturn(new ThemeRecommendationResponse(List.of(
                candidate("balanced", theme()), candidate("vibrant", near), candidate("deep", deep()))));
        assertEquals(List.of("balanced", "deep"), localService.recommend(1L, input).candidates().stream()
                .map(ThemeRecommendationCandidate::id).toList());
        assertTrue(output.getOut().contains("stage=DIVERSITY rule=CANDIDATE_VARIATION_TOO_SUBTLE"));
        assertTrue(output.getOut().contains("threshold=score>=2.5"));

        when(client.recommend(any(), any(), anyString())).thenReturn(new ThemeRecommendationResponse(List.of(
                candidate("balanced", theme()), candidate("vibrant", deep()), candidate("deep", vibrant()))));
        assertEquals(List.of("balanced"), localService.recommend(1L, input).candidates().stream()
                .map(ThemeRecommendationCandidate::id).toList());
        assertTrue(output.getOut().contains("stage=VARIATION_DIRECTION rule=VARIATION_WRONG_DIRECTION"));
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
            assertTrue(instructions.contains("Balanced is the highest-priority candidate"));
            assertTrue(instructions.contains("reference palette: design it internally first"));
            assertTrue(instructions.contains("poster-faithful real-world festival theme"));
            assertTrue(instructions.contains("Do not sacrifice Balanced's validity or"));
            assertTrue(instructions.contains("Balanced (name it Original or Balanced)"));
            assertTrue(instructions.contains("Vibrant (name it Light)"));
            assertTrue(instructions.contains("derive a secondary variation from valid Balanced"));
            assertTrue(instructions.contains("Deep: derive another secondary variation from valid Balanced"));
            assertTrue(instructions.contains("Preserve palette cohesion while satisfying every validation rule"));
            assertTrue(instructions.contains("mainly by lightness, saturation and depth"));
            assertTrue(instructions.contains("Status semantics take priority over brand palette harmony"));
            assertTrue(instructions.contains("success MUST be green (HSB hue 75..175)"));
            assertTrue(instructions.contains("warning") && instructions.contains("yellow/amber/orange (20..65)"));
            assertTrue(instructions.contains("danger red (345..360 or 0..15)"));
            assertTrue(instructions.contains("Pending is more flexible but must remain visibly distinct"));
            assertTrue(instructions.contains("NEVER make success blue, gray or purple"));
            assertTrue(instructions.contains("start within the poster-derived festival palette"));
            assertTrue(instructions.contains("(1) lightness, (2) saturation, (3) warm/cool bias, then (4) subtle hue shifts"));
            assertTrue(instructions.contains("add only the minimum"));
            assertTrue(instructions.contains("as a last resort"));
            assertTrue(instructions.contains("Check all 15"));
            assertTrue(instructions.contains("Different HEX values alone are not"));
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

    @Test void monochromaticPosterPrioritizesPaletteCohesionAndCategoryDistance() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200).setBody(completed(mapper.writeValueAsString(response()))));
            var client = new OpenAiThemeRecommendationClient(mapper, "test-secret", "gpt-4o-mini",
                    server.url("/v1/responses").toString());
            var violetColors = List.of("#B6A9D8", "#A896E5", "#D9D6E8", "#776AA6", "#7D6CD6", "#7BA6D8");

            assertEquals(response(), client.recommend(png, violetColors, "#A896E5"));

            JsonNode body = mapper.readTree(server.takeRequest().getBody().readUtf8());
            String posterInput = body.at("/input/1/content/0/text").asText();
            String instructions = body.at("/input/0/content").asText();
            assertTrue(posterInput.contains(String.join(", ", violetColors)));
            assertTrue(posterInput.contains("Administrator-selected base color: #A896E5"));
            assertTrue(instructions.contains("Reflect the selected base"));
            assertTrue(instructions.contains("extracted poster colors strongly in its four brand colors"));
            assertTrue(instructions.contains("success MUST be green"));
            assertTrue(instructions.contains("For a violet/lavender poster"));
            assertTrue(instructions.contains("pale lavender, vivid violet, muted mauve, blue-violet, periwinkle"));
            assertTrue(instructions.contains("cool desaturated blue-purple"));
            assertTrue(instructions.contains("(1) lightness, (2) saturation, (3) warm/cool bias, then (4) subtle hue shifts"));
            assertTrue(instructions.contains("unrelated yellow, coral, teal or green must not be the default"));
            assertTrue(instructions.contains("as a last resort"));
            assertFalse(instructions.contains("Example hue-zone guide"));
            assertTrue(instructions.contains("RGB Euclidean distance at least 35 for every pair"));
            assertTrue(instructions.contains("Light and Deep should vary category lightness, saturation and depth from Balanced"));
            assertEquals(3, body.at("/text/format/schema/properties/candidates/minItems").asInt());
            assertEquals(3, body.at("/text/format/schema/properties/candidates/maxItems").asInt());
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
        return new ThemeData(new ThemeData.Brand("#F69324", "#F5D251", "#B74FB4", "#FA7840"),
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
