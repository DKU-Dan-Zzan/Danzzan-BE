package com.danzzan.domain.festival.theme.service;

import com.danzzan.domain.festival.theme.dto.ThemeData;
import com.danzzan.domain.festival.theme.exception.ThemeRecommendationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Responses API transport. Never logs the key, poster bytes, prompt, or upstream body. */
@Component
public class OpenAiThemeRecommendationClient implements ThemeRecommendationClient {
    private static final Duration TIMEOUT = Duration.ofSeconds(45);
    private static final int MAX_POSTER_BYTES = 5 * 1024 * 1024;
    private static final String INSTRUCTIONS = """
            Analyze the supplied official festival poster image itself, not only the supplied swatches.
            Recommend exactly 14 #RRGGBB colors for a cohesive festival website with bright white/near-white surfaces.
            The selected base color is the administrator's preference: reflect it naturally in primary or a related brand color; do not ignore it.
            brand.primary: main identity and common active states. brand.secondary: harmonious supporting tone.
            brand.accent: distinct small indicators/icons/badges. brand.strong: a deeper or more intense brand tone
            for prominent CTAs, emphasized text, borders and glow, not simply near-black.
            status.success must remain green; warning yellow/amber/orange; danger red; pending visibly distinct.
            category.pub, foodTruck, experience, event, restArea, facility must be mutually distinguishable
            on map markers and polygons, reasonably color-vision-friendly and harmonious with the poster.
            Do not merely copy a single brand color into all categories.
            Do not output page backgrounds, pure white/black, neutral gray, overlays, shadow opacity or explanation.
            """;

    private final ObjectMapper mapper;
    private final HttpClient http;
    private final String apiKey;
    private final String model;
    private final URI endpoint;

    public OpenAiThemeRecommendationClient(ObjectMapper mapper,
                                           @Value("${openai.api-key:}") String apiKey,
                                           @Value("${openai.theme-model:gpt-4o-mini}") String model,
                                           @Value("${openai.base-url:https://api.openai.com/v1/responses}") String endpoint) {
        this.mapper = mapper;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.apiKey = apiKey;
        this.model = model;
        this.endpoint = URI.create(endpoint);
    }

    @Override
    public ThemeData recommend(byte[] poster, List<String> sourceColors, String selectedBaseColor) {
        if (apiKey == null || apiKey.isBlank()) throw failure(HttpStatus.SERVICE_UNAVAILABLE,
                "OPENAI_KEY_MISSING", "AI recommendation is not configured. Set OPENAI_API_KEY on the backend.");
        String mime = posterMime(poster);
        String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(poster);
        String userText = "Extracted poster colors: " + String.join(", ", sourceColors)
                + ". Administrator-selected base color: " + selectedBaseColor + ".";
        Map<String, Object> payload = Map.of(
                "model", model,
                "store", false,
                "max_output_tokens", 700,
                "input", List.of(
                        Map.of("role", "developer", "content", INSTRUCTIONS),
                        Map.of("role", "user", "content", List.of(
                                Map.of("type", "input_text", "text", userText),
                                Map.of("type", "input_image", "image_url", dataUrl, "detail", "high")))),
                "text", Map.of("format", Map.of("type", "json_schema", "name", "festival_theme_data",
                        "strict", true, "schema", schema())));
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) throw failure(HttpStatus.TOO_MANY_REQUESTS,
                    "OPENAI_RATE_LIMIT", "AI request limit reached. Please try again later.");
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw failure(HttpStatus.BAD_GATEWAY,
                    "OPENAI_MODEL_ERROR", "AI model request failed. Check the model and backend account configuration.");
            JsonNode envelope;
            try {
                envelope = mapper.readTree(response.body());
            } catch (IOException malformed) {
                throw failure(HttpStatus.BAD_GATEWAY, "OPENAI_MALFORMED_OUTPUT",
                        "AI returned an invalid response structure.");
            }
            if (!"completed".equals(envelope.path("status").asText())) throw failure(HttpStatus.BAD_GATEWAY,
                    "OPENAI_MODEL_ERROR", "AI model did not complete the recommendation.");
            for (JsonNode output : envelope.path("output")) {
                for (JsonNode content : output.path("content")) {
                    if ("output_text".equals(content.path("type").asText()) && content.path("text").isTextual()) {
                        try {
                            return mapper.readValue(content.path("text").asText(), ThemeData.class);
                        } catch (IOException malformed) {
                            throw failure(HttpStatus.BAD_GATEWAY, "OPENAI_MALFORMED_OUTPUT",
                                    "AI returned an invalid theme structure.");
                        }
                    }
                }
            }
            throw failure(HttpStatus.BAD_GATEWAY, "OPENAI_MALFORMED_OUTPUT",
                    "AI returned no theme data.");
        } catch (java.net.http.HttpTimeoutException timeout) {
            throw failure(HttpStatus.GATEWAY_TIMEOUT, "OPENAI_TIMEOUT", "AI recommendation timed out. Please try again.");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw failure(HttpStatus.BAD_GATEWAY, "OPENAI_MODEL_ERROR", "AI recommendation was interrupted.");
        } catch (IOException transport) {
            throw failure(HttpStatus.BAD_GATEWAY, "OPENAI_MODEL_ERROR", "AI recommendation service is unavailable.");
        }
    }

    private String posterMime(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_POSTER_BYTES) throw failure(
                HttpStatus.UNPROCESSABLE_ENTITY, "POSTER_UNSUPPORTED", "Saved poster must be a JPG or PNG up to 5 MB.");
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P'
                && bytes[2] == 'N' && bytes[3] == 'G') return "image/png";
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) return "image/jpeg";
        throw failure(HttpStatus.UNPROCESSABLE_ENTITY, "POSTER_UNSUPPORTED", "Saved poster must be a JPG or PNG up to 5 MB.");
    }

    private Map<String, Object> schema() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("brand", group("primary", "secondary", "accent", "strong"));
        fields.put("status", group("success", "warning", "danger", "pending"));
        fields.put("category", group("pub", "foodTruck", "experience", "event", "restArea", "facility"));
        return Map.of("type", "object", "properties", fields,
                "required", List.copyOf(fields.keySet()), "additionalProperties", false);
    }

    private Map<String, Object> group(String... names) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (String name : names) fields.put(name, Map.of("type", "string"));
        return Map.of("type", "object", "properties", fields,
                "required", List.of(names), "additionalProperties", false);
    }

    private ThemeRecommendationException failure(HttpStatus status, String code, String message) {
        return new ThemeRecommendationException(status, code, message);
    }
}
