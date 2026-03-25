package com.danzzan.domain.boothmap.model.entity;

import java.util.Locale;

public enum BoothSubType {
    TOILET,
    SMOKING_AREA;

    public static BoothSubType resolve(Booth booth) {
        if (booth == null || booth.getType() != BoothType.FACILITY) {
            return null;
        }

        String normalizedName = normalize(booth.getName());
        if (normalizedName.contains("화장실")) {
            return TOILET;
        }
        if (normalizedName.contains("흡연구역") || normalizedName.contains("흡연 구역")) {
            return SMOKING_AREA;
        }

        return null;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
