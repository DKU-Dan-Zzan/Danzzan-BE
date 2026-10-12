package com.danzzan.domain.festival.theme.service;

import com.danzzan.domain.festival.theme.dto.ThemeData;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Bounded, deterministic repair of AI output only. Final acceptance belongs to ThemeRecommendationValidator. */
public final class AiThemePaletteAdjuster {
    private static final double MIN_DISTANCE = 35;
    private static final double REPAIR_DISTANCE = 42;
    private static final int[] HUE_STEPS = {0, -4, 4, -8, 8, -14, 14, -22, 22, -35, 35, -55, 55};
    private static final float[] SATURATION_STEPS = {0, -.08f, .08f, -.16f, .16f, -.25f, .25f, -.35f, .35f};
    private static final float[] BRIGHTNESS_STEPS = {0, -.08f, .08f, -.16f, .16f, -.26f, .26f, -.38f, .38f, -.50f, .50f};
    private static final int[] FALLBACK_HUES = {0, -12, 12, -25, 25, -40, 40, -60, 60, -90, 90};
    private static final float[] FALLBACK_SATURATIONS = {0, .12f, .24f, .36f, .50f, .65f};
    private static final float[] FALLBACK_BRIGHTNESSES = {.24f, .34f, .44f, .54f, .64f, .74f, .84f, .94f};

    public ThemeData adjust(ThemeData data, String selectedBaseColor) {
        if (data == null || data.brand() == null || data.status() == null || data.category() == null) return data;
        ThemeData.Status status = adjustStatus(data.status());
        ThemeData.Category category = adjustCategory(data.category(), data.brand(), selectedBaseColor);
        if (status.equals(data.status()) && category.equals(data.category())) return data;
        return new ThemeData(data.brand(), status, category);
    }

    private ThemeData.Status adjustStatus(ThemeData.Status status) {
        return new ThemeData.Status(
                liftNearThresholdSaturation(status.success(), 75, 175),
                liftNearThresholdSaturation(status.warning(), 20, 65),
                liftNearThresholdSaturation(status.danger(), 345, 15),
                status.pending());
    }

    private String liftNearThresholdSaturation(String hex, int from, int to) {
        if (!isHex(hex)) return hex;
        Color color = Color.decode(hex);
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        double hue = hsb[0] * 360;
        boolean semanticHue = from <= to ? hue >= from && hue <= to : hue >= from || hue <= to;
        if (!semanticHue || hsb[1] < .20f || hsb[1] >= .25f) return hex;
        return hex(new Color(Color.HSBtoRGB(hsb[0], .28f, hsb[2])));
    }

    private ThemeData.Category adjustCategory(ThemeData.Category category, ThemeData.Brand brand, String selectedBaseColor) {
        List<String> hexes = Arrays.asList(category.pub(), category.foodTruck(), category.experience(),
                category.event(), category.restArea(), category.facility());
        if (hexes.stream().anyMatch(hex -> !isHex(hex))) return category;
        List<Color> original = hexes.stream().map(Color::decode).toList();
        if (allSeparated(original, MIN_DISTANCE)) return category;
        Color reference = isHex(selectedBaseColor) ? Color.decode(selectedBaseColor)
                : isHex(brand.primary()) ? Color.decode(brand.primary()) : original.get(0);
        List<Color> repaired = repair(original, reference);
        if (repaired == null) repaired = fallback(original, reference);
        if (repaired == null || !allSeparated(repaired, MIN_DISTANCE)) return category;
        return new ThemeData.Category(render(hexes, original, repaired, 0), render(hexes, original, repaired, 1),
                render(hexes, original, repaired, 2), render(hexes, original, repaired, 3),
                render(hexes, original, repaired, 4), render(hexes, original, repaired, 5));
    }

    private String render(List<String> hexes, List<Color> original, List<Color> repaired, int index) {
        return repaired.get(index).equals(original.get(index)) ? hexes.get(index) : hex(repaired.get(index));
    }

    private List<Color> repair(List<Color> original, Color reference) {
        List<Color> chosen = new ArrayList<>(6);
        for (Color color : original) {
            if (separated(color, chosen, MIN_DISTANCE)) {
                chosen.add(color); // Already valid colors stay byte-for-byte unchanged.
                continue;
            }
            Color replacement = nearestLocal(color, reference, chosen);
            if (replacement == null) return null;
            chosen.add(replacement);
        }
        return chosen;
    }

    private Color nearestLocal(Color original, Color reference, List<Color> chosen) {
        float[] hsb = Color.RGBtoHSB(original.getRed(), original.getGreen(), original.getBlue(), null);
        float[] referenceHsb = Color.RGBtoHSB(reference.getRed(), reference.getGreen(), reference.getBlue(), null);
        float hue = hsb[1] < .08f ? referenceHsb[0] : hsb[0];
        Color best = null;
        double bestScore = Double.MAX_VALUE;
        for (int hueStep : HUE_STEPS) {
            for (float saturationStep : SATURATION_STEPS) {
                for (float brightnessStep : BRIGHTNESS_STEPS) {
                    Color candidate = new Color(Color.HSBtoRGB(wrap(hue + hueStep / 360f),
                            clamp(hsb[1] + saturationStep), clamp(hsb[2] + brightnessStep)));
                    if (!separated(candidate, chosen, REPAIR_DISTANCE)) continue;
                    double score = distance(original, candidate) + Math.abs(hueStep) * .45
                            + Math.abs(saturationStep) * 8 + Math.abs(brightnessStep) * 4;
                    if (score < bestScore) {
                        best = candidate;
                        bestScore = score;
                    }
                }
            }
        }
        return best;
    }

    private List<Color> fallback(List<Color> original, Color reference) {
        float[] hsb = Color.RGBtoHSB(reference.getRed(), reference.getGreen(), reference.getBlue(), null);
        List<Color> chosen = new ArrayList<>(6);
        for (Color source : original) {
            Color best = null;
            double bestScore = Double.MAX_VALUE;
            for (int hueStep : FALLBACK_HUES) {
                for (float saturation : FALLBACK_SATURATIONS) {
                    for (float brightness : FALLBACK_BRIGHTNESSES) {
                        Color candidate = new Color(Color.HSBtoRGB(wrap(hsb[0] + hueStep / 360f), saturation, brightness));
                        if (!separated(candidate, chosen, REPAIR_DISTANCE)) continue;
                        double score = distance(source, candidate) + Math.abs(hueStep) * .6;
                        if (score < bestScore) {
                            best = candidate;
                            bestScore = score;
                        }
                    }
                }
            }
            if (best == null) return null;
            chosen.add(best);
        }
        return chosen;
    }

    private boolean allSeparated(List<Color> colors, double minimum) {
        for (int i = 0; i < colors.size(); i++) {
            for (int j = i + 1; j < colors.size(); j++) {
                if (distance(colors.get(i), colors.get(j)) < minimum) return false;
            }
        }
        return true;
    }

    private boolean separated(Color candidate, List<Color> chosen, double minimum) {
        return chosen.stream().allMatch(other -> distance(candidate, other) >= minimum);
    }

    private double distance(Color a, Color b) {
        int dr = a.getRed() - b.getRed(), dg = a.getGreen() - b.getGreen(), db = a.getBlue() - b.getBlue();
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }

    private boolean isHex(String value) {
        return value != null && value.matches("#[0-9A-Fa-f]{6}");
    }

    private String hex(Color color) {
        return String.format("#%06X", color.getRGB() & 0xFFFFFF);
    }

    private float clamp(float value) {
        return Math.max(0, Math.min(1, value));
    }

    private float wrap(float hue) {
        return (hue % 1 + 1) % 1;
    }
}
