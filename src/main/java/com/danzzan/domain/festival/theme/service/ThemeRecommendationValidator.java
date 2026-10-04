package com.danzzan.domain.festival.theme.service;

import com.danzzan.domain.festival.theme.dto.ThemeData;
import com.danzzan.domain.festival.theme.exception.ThemeRecommendationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ThemeRecommendationValidator {
    private final Validator validator;

    public void validate(ThemeData data) {
        if (data == null || !validator.validate(data).isEmpty()) throw invalid();
        if (!inHue(data.status().success(), 75, 175)
                || !inHue(data.status().warning(), 20, 65)
                || !inHue(data.status().danger(), 345, 15)) throw invalid();

        List<String> categories = List.of(data.category().pub(), data.category().foodTruck(),
                data.category().experience(), data.category().event(), data.category().restArea(),
                data.category().facility());
        for (int i = 0; i < categories.size(); i++) {
            for (int j = i + 1; j < categories.size(); j++) {
                if (distance(categories.get(i), categories.get(j)) < 35) throw invalid();
            }
        }
        if (distance(data.brand().primary(), data.brand().accent()) < 20
                && distance(data.brand().primary(), data.brand().strong()) < 20) throw invalid();
    }

    private boolean inHue(String hex, float from, float to) {
        Color color = Color.decode(hex);
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        float hue = hsb[0] * 360;
        return hsb[1] >= 0.25 && (from <= to ? hue >= from && hue <= to : hue >= from || hue <= to);
    }

    private double distance(String first, String second) {
        Color a = Color.decode(first);
        Color b = Color.decode(second);
        int dr = a.getRed() - b.getRed();
        int dg = a.getGreen() - b.getGreen();
        int db = a.getBlue() - b.getBlue();
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }

    private ThemeRecommendationException invalid() {
        return new ThemeRecommendationException(HttpStatus.BAD_GATEWAY, "AI_THEME_VALIDATION_FAILED",
                "AI recommendation did not satisfy theme color rules. Please try again.");
    }
}
