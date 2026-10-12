package com.danzzan.domain.festival.controller;

import com.danzzan.domain.festival.dto.response.PosterColorResponse;
import com.danzzan.domain.festival.service.FestivalPosterColorService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/festival/{festivalSettingId}/theme-colors")
@PreAuthorize("@userAdminAuthorizationService.hasOperationsRole(authentication)")
public class AdminPosterColorController {
    private final FestivalPosterColorService service;

    @PostMapping("/extract")
    public PosterColorResponse extract(@PathVariable Long festivalSettingId) {
        return service.extract(festivalSettingId);
    }
}
