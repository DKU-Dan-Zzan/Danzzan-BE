package com.danzzan.domain.festival.theme.service;

import com.danzzan.domain.festival.theme.dto.ThemeRecommendationResponse;
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
            Return exactly three related variations of one poster-derived theme in this order: balanced, vibrant, deep.
            Balanced is the highest-priority candidate and the reference palette: design it internally first as the most
            usable, stable, poster-faithful real-world festival theme. Check its status hue, brand distinction and category
            distance rules before deriving Light and Deep from that same palette. Do not sacrifice Balanced's validity or
            visual identity to make the variations more creative or to maximize hue diversity.
            Each candidate must have its fixed id, a short name, a one- or two-sentence description (at most 160 characters),
            and exactly 14 #RRGGBB colors. Keep bright white/near-white surfaces in all three; none is a dark theme.
            Balanced (name it Original or Balanced): the production-ready primary recommendation. Reflect the selected base
            color and extracted poster colors strongly in its four brand colors and keep its six map category colors within
            the same festival palette whenever possible. Preserve palette cohesion while satisfying every validation rule.
            Vibrant (name it Light): derive a secondary variation from valid Balanced, keeping its brand hue and visual identity,
            with modestly higher lightness and gently adjusted saturation for a brighter, softer feel.
            Deep: derive another secondary variation from valid Balanced, keeping its brand hue and visual identity, but
            deepen primary, strong and selected category colors and improve contrast without making a dark theme.
            Distinguish the three mainly by lightness, saturation and depth, not by different brand hue families.
            Use the administrator-selected base color as a central reference in every candidate. Keep brand palettes visibly
            different through those variations; do not repeat the same primary in all three or make nearly identical palettes.
            brand.primary: main identity and common active states. brand.secondary: harmonious supporting tone.
            brand.accent: distinct small indicators/icons/badges. brand.strong: a deeper or more intense brand tone
            for prominent CTAs, emphasized text, borders and glow, not simply near-black.
            Status semantics take priority over brand palette harmony: success MUST be green (HSB hue 75..175), warning
            yellow/amber/orange (20..65), danger red (345..360 or 0..15), each with HSB saturation at least 0.25.
            Pending is more flexible but must remain visibly distinct from the other statuses. Harmonize status tones with
            the festival only within those semantic hue families. Even when selectedBaseColor or poster colors are blue,
            gray or purple, NEVER make success blue, gray or purple.
            Category.pub, foodTruck, experience, event, restArea and facility must be distinct map marker/polygon colors,
            but start within the poster-derived festival palette, especially for Balanced. Separate all six first by
            (1) lightness, (2) saturation, (3) warm/cool bias, then (4) subtle hue shifts. For a violet/lavender poster,
            try pale lavender, vivid violet, muted mauve, blue-violet, periwinkle and cool desaturated blue-purple, adjusted
            far enough apart to pass RGB Euclidean distance at least 35 for every pair. Different HEX values alone are not
            enough: check all 15 category pairs. If this palette family cannot meet the distance rule, add only the minimum
            harmonious supporting hue needed as a last resort; unrelated yellow, coral, teal or green must not be the default.
            Never weaken the distance rule, but do not turn a cohesive poster theme into a rainbow just to separate categories.
            Light and Deep should vary category lightness, saturation and depth from Balanced rather than replace its palette.
            Check all 15 category pairs before returning, without outputting calculations.
            In each candidate, primary must differ from accent or strong by RGB distance at least 20.
            Do not merely copy a single brand color into all categories.
            Return candidate descriptions only, no extra explanation. Do not output page backgrounds, pure white/black,
            neutral gray, overlays or shadow opacity.
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
    public ThemeRecommendationResponse recommend(byte[] poster, List<String> sourceColors, String selectedBaseColor) {
        if (apiKey == null || apiKey.isBlank()) throw failure(HttpStatus.SERVICE_UNAVAILABLE,
                "OPENAI_KEY_MISSING", "AI recommendation is not configured. Set OPENAI_API_KEY on the backend.");
        String mime = posterMime(poster);
        String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(poster);
        String userText = "Extracted poster colors: " + String.join(", ", sourceColors)
                + ". Administrator-selected base color: " + selectedBaseColor + ".";
        Map<String, Object> payload = Map.of(
                "model", model,
                "store", false,
                "max_output_tokens", 2400,
                "input", List.of(
                        Map.of("role", "developer", "content", INSTRUCTIONS),
                        Map.of("role", "user", "content", List.of(
                                Map.of("type", "input_text", "text", userText),
                                Map.of("type", "input_image", "image_url", dataUrl, "detail", "high")))),
                "text", Map.of("format", Map.of("type", "json_schema", "name", "festival_theme_candidates",
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
                            return mapper.readValue(content.path("text").asText(), ThemeRecommendationResponse.class);
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
        Map<String, Object> themeData = Map.of("type", "object", "properties", fields,
                "required", List.copyOf(fields.keySet()), "additionalProperties", false);
        Map<String, Object> candidateFields = new LinkedHashMap<>();
        candidateFields.put("id", Map.of("type", "string", "enum", List.of("balanced", "vibrant", "deep")));
        candidateFields.put("name", Map.of("type", "string"));
        candidateFields.put("description", Map.of("type", "string"));
        candidateFields.put("themeData", themeData);
        Map<String, Object> candidate = Map.of("type", "object", "properties", candidateFields,
                "required", List.copyOf(candidateFields.keySet()), "additionalProperties", false);
        return Map.of("type", "object", "properties", Map.of("candidates", Map.of("type", "array",
                        "items", candidate, "minItems", 3, "maxItems", 3)),
                "required", List.of("candidates"), "additionalProperties", false);
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
