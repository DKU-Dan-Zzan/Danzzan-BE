package com.danzzan.domain.festival.theme.service;

import com.danzzan.domain.festival.theme.dto.ThemeData;
import java.util.List;

public interface ThemeRecommendationClient {
    ThemeData recommend(byte[] poster, List<String> sourceColors, String selectedBaseColor);
}
