package com.danzzan.domain.festival.theme.dto;

import java.util.List;

public record ThemeRecommendationResponse(List<ThemeRecommendationCandidate> candidates) {
}
