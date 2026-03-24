package com.danzzan.domain.admin.map.controller;

import com.danzzan.domain.admin.map.dto.request.UpdateMapLocationRequest;
import com.danzzan.domain.admin.map.dto.request.UpdateActiveOperationDateRequest;
import com.danzzan.domain.admin.map.dto.response.AdminMapResponse;
import com.danzzan.domain.admin.map.service.AdminMapService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/map")
@PreAuthorize("@userAdminAuthorizationService.hasAdminRole(authentication)")
public class AdminMapController {

    private final AdminMapService adminMapService;

    @GetMapping
    public AdminMapResponse getAdminMap(@RequestParam(required = false) String date) {
        LocalDate operationDate = (date == null || date.isBlank())
                ? adminMapService.getActiveOperationDate()
                : LocalDate.parse(date);

        return adminMapService.getAdminMap(operationDate);
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

    @PutMapping("/active-date")
    public void updateActiveDate(@RequestBody UpdateActiveOperationDateRequest request) {
        adminMapService.updateActiveDate(request);
    }
}
