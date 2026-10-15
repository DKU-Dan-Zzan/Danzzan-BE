package com.danzzan.domain.festival.theme.service;

import com.danzzan.domain.festival.theme.dto.ThemeData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ThemeColorPolicyTest {
    @Test
    void normalizesOnlySemanticAndMapColorsWhilePubFollowsPrimary() {
        ThemeData.Brand brand = new ThemeData.Brand("#3355AA", "#ABCDEF", "#FFCC00", "#112244");
        ThemeData input = new ThemeData(brand,
                new ThemeData.Status("#111111", "#222222", "#333333", "#444444"),
                new ThemeData.Category("#555555", "#666666", "#777777", "#888888", "#999999", "#AAAAAA"));

        ThemeData normalized = ThemeColorPolicy.normalize(input);

        assertEquals(brand, normalized.brand());
        assertEquals(new ThemeData.Status("#2E7D32", "#B26A00", "#B3261E", "#E3776A"), normalized.status());
        assertEquals(new ThemeData.Category("#3355AA", "#EC4899", "#10B981", "#F6CA3B", "#8B5CF6", "#3B82F6"),
                normalized.category());
    }

    @Test
    void keepsAnAlreadyNormalizedThemeInstance() {
        ThemeData input = new ThemeData(new ThemeData.Brand("#3355AA", "#ABCDEF", "#FFCC00", "#112244"),
                new ThemeData.Status("#2E7D32", "#B26A00", "#B3261E", "#E3776A"),
                new ThemeData.Category("#3355AA", "#EC4899", "#10B981", "#F6CA3B", "#8B5CF6", "#3B82F6"));

        assertSame(input, ThemeColorPolicy.normalize(input));
    }
}
