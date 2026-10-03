package com.danzzan.domain.festival.theme.controller;

import com.danzzan.domain.festival.theme.dto.ThemeResponse;
import com.danzzan.domain.festival.theme.service.ThemeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/festival/{festivalSettingId}/themes")
@Tag(name = "축제 테마", description = "현재 적용 중인 축제 테마 조회 API")
public class FestivalThemeController {
    private final ThemeService themeService;

    /** 204 means no active theme; the client keeps its bundled Theme Core defaults. */
    @GetMapping("/active")
    public ResponseEntity<ThemeResponse> active(@PathVariable Long festivalSettingId) {
        return themeService.active(festivalSettingId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
