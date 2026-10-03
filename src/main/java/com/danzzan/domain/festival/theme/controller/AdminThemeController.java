package com.danzzan.domain.festival.theme.controller;

import com.danzzan.domain.festival.theme.dto.ThemeResponse;
import com.danzzan.domain.festival.theme.dto.ThemeUpsertRequest;
import com.danzzan.domain.festival.theme.service.ThemeService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/festival/{festivalSettingId}/themes")
@PreAuthorize("@userAdminAuthorizationService.hasOperationsRole(authentication)")
@Tag(name = "관리자 축제 테마", description = "축제별 테마 저장 및 활성화 API")
@SecurityRequirement(name = "bearerAuth")
public class AdminThemeController {
    private final ThemeService themeService;

    @GetMapping
    public List<ThemeResponse> list(@PathVariable Long festivalSettingId) {
        return themeService.list(festivalSettingId);
    }

    @GetMapping("/{themeId}")
    public ThemeResponse get(@PathVariable Long festivalSettingId, @PathVariable Long themeId) {
        return themeService.get(festivalSettingId, themeId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ThemeResponse create(@PathVariable Long festivalSettingId, @Valid @RequestBody ThemeUpsertRequest request) {
        return themeService.create(festivalSettingId, request);
    }

    @PutMapping("/{themeId}")
    public ThemeResponse update(@PathVariable Long festivalSettingId, @PathVariable Long themeId,
                                @Valid @RequestBody ThemeUpsertRequest request) {
        return themeService.update(festivalSettingId, themeId, request);
    }

    @DeleteMapping("/{themeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long festivalSettingId, @PathVariable Long themeId) {
        themeService.delete(festivalSettingId, themeId);
    }

    @PutMapping("/{themeId}/activate")
    public ThemeResponse activate(@PathVariable Long festivalSettingId, @PathVariable Long themeId) {
        return themeService.activate(festivalSettingId, themeId);
    }
}
