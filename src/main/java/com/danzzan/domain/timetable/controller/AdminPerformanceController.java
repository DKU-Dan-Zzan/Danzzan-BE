package com.danzzan.domain.timetable.controller;

import com.danzzan.domain.timetable.dto.admin.request.CreatePerformanceRequest;
import com.danzzan.domain.timetable.dto.admin.request.UpdatePerformanceRequest;
import com.danzzan.domain.timetable.dto.admin.response.AdminPerformanceListResponse;
import com.danzzan.domain.timetable.dto.admin.response.AdminPerformanceResponse;
import com.danzzan.domain.timetable.service.AdminPerformanceService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/admin/timetable/performances")
@RequiredArgsConstructor
@Tag(name = "관리자 타임테이블 - 공연", description = "관리자 공연 CRUD API")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("@userAdminAuthorizationService.hasOperationsRole(authentication)")
public class AdminPerformanceController {

    private final AdminPerformanceService adminPerformanceService;

    @GetMapping
    public ResponseEntity<AdminPerformanceListResponse> getPerformancesByDate(
            @RequestParam("date")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        return ResponseEntity.ok(adminPerformanceService.getPerformancesByDate(date));
    }

    @PostMapping
    public ResponseEntity<AdminPerformanceResponse> createPerformance(
            @Valid @RequestBody CreatePerformanceRequest request
    ) {
        return ResponseEntity.ok(adminPerformanceService.createPerformance(request));
    }

    @PatchMapping("/{performanceId}")
    public ResponseEntity<AdminPerformanceResponse> updatePerformance(
            @PathVariable Integer performanceId,
            @Valid @RequestBody UpdatePerformanceRequest request
    ) {
        return ResponseEntity.ok(adminPerformanceService.updatePerformance(performanceId, request));
    }

    @DeleteMapping("/{performanceId}")
    public ResponseEntity<Void> deletePerformance(@PathVariable Integer performanceId) {
        adminPerformanceService.deletePerformance(performanceId);
        return ResponseEntity.noContent().build();
    }
}
