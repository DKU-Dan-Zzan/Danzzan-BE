package com.danzzan.domain.festival.theme.service;

import com.danzzan.domain.festival.theme.dto.ThemeData;

/**
 * Colors whose meaning must stay stable across every festival theme.
 * Brand colors remain theme-specific; the pub marker deliberately follows the primary brand color.
 */
public final class ThemeColorPolicy {
    public static final String SUCCESS = "#2E7D32";
    public static final String WARNING = "#B26A00";
    public static final String DANGER = "#B3261E";
    public static final String PENDING = "#E3776A";
    public static final String FOOD_TRUCK = "#EC4899";
    public static final String EXPERIENCE = "#10B981";
    public static final String EVENT = "#F6CA3B";
    public static final String REST_AREA = "#8B5CF6";
    public static final String FACILITY = "#3B82F6";

    private ThemeColorPolicy() {
    }

    /** Applies the policy for new recommendations and persisted theme writes. Existing stored themes are untouched. */
    public static ThemeData normalize(ThemeData data) {
        if (data == null || data.brand() == null || data.status() == null || data.category() == null) return data;
        ThemeData.Status status = new ThemeData.Status(SUCCESS, WARNING, DANGER, PENDING);
        ThemeData.Category category = new ThemeData.Category(data.brand().primary(), FOOD_TRUCK, EXPERIENCE, EVENT,
                REST_AREA, FACILITY);
        if (status.equals(data.status()) && category.equals(data.category())) return data;
        return new ThemeData(data.brand(), status, category);
    }
}
