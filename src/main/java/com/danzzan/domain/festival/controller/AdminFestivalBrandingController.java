package com.danzzan.domain.festival.controller;

import com.danzzan.domain.festival.dto.response.FestivalBrandingResponse;
import com.danzzan.domain.festival.service.FestivalBrandingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/festival/{festivalSettingId}/branding")
@PreAuthorize("@userAdminAuthorizationService.hasOperationsRole(authentication)")
public class AdminFestivalBrandingController {
    private final FestivalBrandingService service;

    @PostMapping(value = "/poster", consumes = "multipart/form-data")
    public FestivalBrandingResponse uploadPoster(@PathVariable Long festivalSettingId, @RequestPart("file") MultipartFile file) {
        return service.uploadPoster(festivalSettingId, file);
    }

    @PostMapping(value = "/logo", consumes = "multipart/form-data")
    public FestivalBrandingResponse uploadLogo(@PathVariable Long festivalSettingId, @RequestPart("file") MultipartFile file) {
        return service.uploadLogo(festivalSettingId, file);
    }
}
