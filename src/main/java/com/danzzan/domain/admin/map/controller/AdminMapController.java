package com.danzzan.domain.admin.map.controller;

import com.danzzan.domain.admin.map.dto.request.UpdateMapLocationRequest;
import com.danzzan.domain.admin.map.dto.response.AdminMapResponse;
import com.danzzan.domain.admin.map.service.AdminMapService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/map")
public class AdminMapController {

    private final AdminMapService adminMapService;

    @GetMapping
    public AdminMapResponse getAdminMap() {
        return adminMapService.getAdminMap();
    }

    @PatchMapping("/colleges/{collegeId}/location")
    public void updateCollegeLocation(
            @PathVariable Long collegeId,
            @Valid @RequestBody UpdateMapLocationRequest request
    ) {
        adminMapService.updateCollegeLocation(collegeId, request);
    }

    @PatchMapping("/booths/{boothId}/location")
    public void updateBoothLocation(
            @PathVariable Long boothId,
            @Valid @RequestBody UpdateMapLocationRequest request
    ) {
        adminMapService.updateBoothLocation(boothId, request);
    }

    @DeleteMapping("/booths/{boothId}/location")
    public void clearBoothLocation(@PathVariable Long boothId) {
        adminMapService.clearBoothLocation(boothId);
    }
}