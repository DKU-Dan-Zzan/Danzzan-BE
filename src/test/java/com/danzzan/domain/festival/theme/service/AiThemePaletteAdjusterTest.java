package com.danzzan.domain.festival.theme.service;

import com.danzzan.domain.festival.theme.dto.ThemeData;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AiThemePaletteAdjusterTest {
    private final AiThemePaletteAdjuster adjuster = new AiThemePaletteAdjuster();
    private final ThemeData.Brand violetBrand = new ThemeData.Brand("#776AA6", "#D9D6E8", "#A896E5", "#7D6CD6");
    private final ThemeData.Status status = new ThemeData.Status("#4EB15E", "#F5B63E", "#DE3945", "#8C79A6");

    @Test void leavesAlreadyValidDiversePaletteUnchanged() {
        ThemeData data = data(new ThemeData.Category("#8C66B8", "#D97E83", "#5C9C96",
                "#D2B36C", "#789ACB", "#82A568"));
        assertTrue(minDistance(data.category()) >= 35);
        assertSame(data, adjuster.adjust(data, "#776AA6"));
    }

    @Test void repairsExactDuplicatesAndKeepsTheFirstValidColor() {
        ThemeData data = data(new ThemeData.Category("#b6a9d8", "#B6A9D8", "#B6A9D8",
                "#B6A9D8", "#B6A9D8", "#B6A9D8"));
        ThemeData repaired = adjuster.adjust(data, "#A896E5");
        assertEquals("#b6a9d8", repaired.category().pub());
        assertTrue(minDistance(repaired.category()) >= 40);
        assertEquals(data.brand(), repaired.brand());
        assertValid(repaired);
        assertEquals(repaired, adjuster.adjust(data, "#A896E5"));
    }

    @Test void repairsDistanceTwentySixPointNinetyFourAndThirtyFourPointNinetyEight() {
        ThemeData data = data(new ThemeData.Category("#808080", "#9A8781", "#A28882",
                "#544C75", "#CBC3DE", "#5875B8"));
        assertEquals(26.94, distance("#808080", "#9A8781"), .01);
        assertEquals(34.98, distance("#808080", "#A28882"), .01);
        ThemeData repaired = adjuster.adjust(data, "#A896E5");
        assertEquals("#808080", repaired.category().pub());
        assertTrue(minDistance(repaired.category()) >= 35);
        assertValid(repaired);
    }

    @Test void keepsVioletPosterCategoriesInARelatedFamilyWhenPossible() {
        ThemeData data = data(new ThemeData.Category("#B6A9D8", "#A896E5", "#D9D6E8",
                "#776AA6", "#7D6CD6", "#7BA6D8"));
        ThemeData repaired = adjuster.adjust(data, "#A896E5");
        assertTrue(minDistance(repaired.category()) >= 35);
        for (String color : colors(repaired.category())) {
            Color rgb = Color.decode(color);
            float[] hsb = Color.RGBtoHSB(rgb.getRed(), rgb.getGreen(), rgb.getBlue(), null);
            if (hsb[1] >= .10f) assertTrue(hsb[0] * 360 >= 180 && hsb[0] * 360 <= 310, color);
        }
        assertValid(repaired);
    }

    @Test void repairsLowSaturationGrayPosterWithoutIntroducingRainbowColors() {
        ThemeData data = data(new ThemeData.Category("#888888", "#898989", "#8A8A8A",
                "#8B8B8B", "#8C8C8C", "#8D8D8D"));
        ThemeData repaired = adjuster.adjust(data, "#888888");
        assertTrue(minDistance(repaired.category()) >= 35);
        for (String color : colors(repaired.category())) {
            Color rgb = Color.decode(color);
            float saturation = Color.RGBtoHSB(rgb.getRed(), rgb.getGreen(), rgb.getBlue(), null)[1];
            assertTrue(saturation <= .38f, color);
        }
        assertValid(repaired);
    }

    @Test void liftsOnlyNearThresholdSemanticStatusSaturation() {
        ThemeData data = new ThemeData(violetBrand,
                new ThemeData.Status("#99C799", "#F5B63E", "#DE3945", "#8C79A6"),
                new ThemeData.Category("#8C66B8", "#D97E83", "#5C9C96", "#D2B36C", "#789ACB", "#82A568"));
        ThemeData repaired = adjuster.adjust(data, "#A896E5");
        assertNotEquals(data.status().success(), repaired.status().success());
        assertEquals(data.status().warning(), repaired.status().warning());
        assertEquals(data.status().danger(), repaired.status().danger());
        assertEquals(data.status().pending(), repaired.status().pending());
        assertTrue(distance(data.status().success(), repaired.status().success()) < 20);
        assertValid(repaired);
        ThemeData wrongHue = new ThemeData(violetBrand,
                new ThemeData.Status("#7BA6D8", status.warning(), status.danger(), status.pending()), data.category());
        assertEquals("#7BA6D8", adjuster.adjust(wrongHue, "#A896E5").status().success());
    }

    @Test void leavesInvalidHexForTheExistingValidatorInsteadOfReturningSuccess() {
        ThemeData data = data(new ThemeData.Category("invalid", "#A896E5", "#D9D6E8",
                "#776AA6", "#7D6CD6", "#7BA6D8"));
        assertSame(data, adjuster.adjust(data, "#A896E5"));
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertFalse(new ThemeRecommendationValidator(factory.getValidator()).diagnose(data).isEmpty());
        }
    }

    private ThemeData data(ThemeData.Category category) {
        return new ThemeData(violetBrand, status, category);
    }

    private void assertValid(ThemeData data) {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(new ThemeRecommendationValidator(factory.getValidator()).diagnose(data).isEmpty());
        }
    }

    private List<String> colors(ThemeData.Category category) {
        return List.of(category.pub(), category.foodTruck(), category.experience(),
                category.event(), category.restArea(), category.facility());
    }

    private double minDistance(ThemeData.Category category) {
        List<String> colors = colors(category);
        double minimum = Double.MAX_VALUE;
        for (int i = 0; i < colors.size(); i++) {
            for (int j = i + 1; j < colors.size(); j++) minimum = Math.min(minimum, distance(colors.get(i), colors.get(j)));
        }
        return minimum;
    }

    private double distance(String first, String second) {
        Color a = Color.decode(first), b = Color.decode(second);
        int dr = a.getRed() - b.getRed(), dg = a.getGreen() - b.getGreen(), db = a.getBlue() - b.getBlue();
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }
}
