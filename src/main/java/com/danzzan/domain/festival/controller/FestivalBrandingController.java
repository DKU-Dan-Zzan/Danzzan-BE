package com.danzzan.domain.festival.controller;

import com.danzzan.domain.festival.dto.response.FestivalBrandingResponse;
import com.danzzan.domain.festival.service.FestivalBrandingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/festival/{festivalSettingId}/branding")
public class FestivalBrandingController {
    private final FestivalBrandingService service;

    @GetMapping
    public FestivalBrandingResponse get(@PathVariable Long festivalSettingId) {
        return service.get(festivalSettingId);
    }
}
