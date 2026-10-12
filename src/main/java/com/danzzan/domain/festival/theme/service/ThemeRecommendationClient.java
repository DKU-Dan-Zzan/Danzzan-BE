package com.danzzan.domain.festival.theme.service;

import com.danzzan.domain.festival.theme.dto.ThemeRecommendationResponse;
import java.util.List;

public interface ThemeRecommendationClient {
    ThemeRecommendationResponse recommend(byte[] poster, List<String> sourceColors, String selectedBaseColor);
}
