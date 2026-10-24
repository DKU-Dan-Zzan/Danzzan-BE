package com.danzzan.domain.festival.theme.service;

import com.danzzan.domain.festival.theme.dto.ThemeData;
import com.danzzan.domain.festival.theme.exception.ThemeRecommendationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ThemeRecommendationValidator {
    private final Validator validator;

    public void validate(ThemeData data) {
        if (!diagnose(data).isEmpty()) throw invalid();
    }

    public record Failure(String rule, Map<String, String> colors, Map<String, Double> measured, String threshold) {}

    /** Collect every failed rule without changing the existing pass/fail thresholds. */
    public List<Failure> diagnose(ThemeData data) {
        List<Failure> failures = new ArrayList<>();
        if (data == null) {
            failures.add(new Failure("THEME_DATA_MISSING", Map.of(), Map.of(), "14 required #RRGGBB colors"));
            return failures;
        }
        validator.validate(data).forEach(violation -> failures.add(new Failure("HEX_OR_REQUIRED_FIELD",
                Map.of(violation.getPropertyPath().toString(), safeColor(violation.getInvalidValue())),
                Map.of(), "required #RRGGBB")));
        if (!failures.isEmpty()) return failures; // Invalid HEX must never reach Color.decode.

        checkStatus(failures, "success", data.status().success(), 75, 175);
        checkStatus(failures, "warning", data.status().warning(), 20, 65);
        checkStatus(failures, "danger", data.status().danger(), 345, 15);

        double accentDistance = distance(data.brand().primary(), data.brand().accent());
        double strongDistance = distance(data.brand().primary(), data.brand().strong());
        if (accentDistance < 20 && strongDistance < 20) failures.add(new Failure("BRAND_PRIMARY_NOT_DISTINCT",
                Map.of("primary", data.brand().primary(), "accent", data.brand().accent(),
                        "strong", data.brand().strong()),
                Map.of("primaryAccentDistance", accentDistance, "primaryStrongDistance", strongDistance),
                "primaryAccentDistance>=20 OR primaryStrongDistance>=20"));
        return failures;
    }

    private void checkStatus(List<Failure> failures, String status, String hex, float from, float to) {
        Color color = Color.decode(hex);
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        double hue = hsb[0] * 360;
        double saturation = hsb[1];
        if (saturation < 0.25) failures.add(new Failure("STATUS_" + status.toUpperCase(Locale.ROOT) + "_SATURATION_TOO_LOW",
                Map.of(status, hex), Map.of("saturation", saturation), "saturation>=0.25"));
        if (!(from <= to ? hue >= from && hue <= to : hue >= from || hue <= to)) {
            failures.add(new Failure("STATUS_" + status.toUpperCase(Locale.ROOT) + "_HUE_OUT_OF_RANGE",
                    Map.of(status, hex), Map.of("hue", hue), "hue=" + from + ".." + to));
        }
    }

    private double distance(String first, String second) {
        Color a = Color.decode(first);
        Color b = Color.decode(second);
        int dr = a.getRed() - b.getRed();
        int dg = a.getGreen() - b.getGreen();
        int db = a.getBlue() - b.getBlue();
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }

    private String safeColor(Object value) {
        if (value == null) return "<null>";
        if (!(value instanceof String color)) return "<invalid color>";
        if (color.isBlank()) return "<blank>";
        return color.matches("#[0-9A-Fa-f]{0,6}") ? color : "<invalid color>";
    }

    private ThemeRecommendationException invalid() {
        return new ThemeRecommendationException(HttpStatus.BAD_GATEWAY, "AI_THEME_VALIDATION_FAILED",
                "AI recommendation did not satisfy theme color rules. Please try again.");
    }
}
