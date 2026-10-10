package com.danzzan.domain.home.controller;

import com.danzzan.domain.home.model.dto.AdminHomeImageDto;
import com.danzzan.domain.home.model.dto.HomeImageOrderRequest;
import com.danzzan.domain.home.service.AdminHomeImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/festival/{festivalSettingId}/home-images")
@PreAuthorize("@userAdminAuthorizationService.hasOperationsRole(authentication)")
public class AdminHomeImageController {
    private final AdminHomeImageService service;

    @GetMapping
    public List<AdminHomeImageDto> list(@PathVariable Long festivalSettingId) {
        return service.list(festivalSettingId);
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminHomeImageDto upload(@PathVariable Long festivalSettingId, @RequestPart("file") MultipartFile file) {
        return service.upload(festivalSettingId, file);
    }

    @DeleteMapping("/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long festivalSettingId, @PathVariable Long imageId) {
        service.delete(festivalSettingId, imageId);
    }

    @PutMapping("/order")
    public List<AdminHomeImageDto> reorder(@PathVariable Long festivalSettingId, @RequestBody HomeImageOrderRequest request) {
        return service.reorder(festivalSettingId, request.imageIds());
    }
}
