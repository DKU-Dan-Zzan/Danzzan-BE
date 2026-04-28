package com.danzzan.domain.admin.map.controller;

import com.danzzan.domain.admin.map.dto.request.UpdateAdminBoothRequest;
import com.danzzan.domain.admin.map.dto.request.UpdateAdminPubRequest;
import com.danzzan.domain.admin.map.dto.request.UpdateMapLocationRequest;
import com.danzzan.domain.admin.map.dto.request.UpsertAdminPubOperationRequest;
import com.danzzan.domain.admin.map.dto.response.AdminBoothManagementResponse;
import com.danzzan.domain.admin.map.dto.response.AdminMapResponse;
import com.danzzan.domain.admin.map.service.AdminBoothManagementService;
import com.danzzan.domain.admin.map.service.AdminMapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/map")
@Tag(name = "관리자 지도", description = "관리자 지도/Booth 관리 API")
@SecurityRequirement(name = "bearerAuth")
public class AdminMapController {

    private final AdminMapService adminMapService;
    private final AdminBoothManagementService adminBoothManagementService;

    @GetMapping
    @Operation(summary = "관리자 지도 조회", description = "지도 편집 탭용 관리자 맵 데이터를 조회합니다.")
    public AdminMapResponse getAdminMap(
            @Parameter(description = "조회할 운영 날짜", example = "2026-05-12")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return adminMapService.getAdminMap(date);
    }

    @GetMapping("/booth-management")
    @Operation(summary = "Booth 탭 관리자 데이터 조회", description = "Booth 탭용 booth, pub, pub_operation 데이터를 조회합니다.")
    public AdminBoothManagementResponse getBoothManagement(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return adminBoothManagementService.getBoothManagement(date);
    }

    @PatchMapping("/colleges/{collegeId}/location")
    @Operation(summary = "단과대 위치 수정", description = "지도 편집 탭에서 단과대 마커 위치를 수정합니다.")
    public void updateCollegeLocation(
            @PathVariable Long collegeId,
            @Valid @RequestBody UpdateMapLocationRequest request
    ) {
        adminMapService.updateCollegeLocation(collegeId, request);
    }

    @PatchMapping("/booths/{boothId}/location")
    @Operation(summary = "부스 위치 수정", description = "지도 편집 탭에서 부스 마커 위치를 수정합니다.")
    public void updateBoothLocation(
            @PathVariable Long boothId,
            @Valid @RequestBody UpdateMapLocationRequest request
    ) {
        adminMapService.updateBoothLocation(boothId, request);
    }

    @DeleteMapping("/booths/{boothId}/location")
    @Operation(summary = "부스 위치 제거", description = "지도 편집 탭에서 부스 좌표를 제거합니다.")
    public void clearBoothLocation(@PathVariable Long boothId) {
        adminMapService.clearBoothLocation(boothId);
    }

    @PatchMapping("/booths/{boothId}/management")
    @Operation(summary = "Booth 탭 부스 정보 수정", description = "booth description 및 booth_operation 정보를 수정합니다.")
    public void updateBoothManagement(
            @PathVariable Long boothId,
            @Valid @RequestBody UpdateAdminBoothRequest request
    ) {
        adminBoothManagementService.updateBoothManagement(boothId, request);
    }

    @PatchMapping("/pubs/{pubId}/management")
    @Operation(summary = "Booth 탭 주점 정보 수정", description = "pub intro, description, instagram 정보를 수정합니다.")
    public void updatePubManagement(
            @PathVariable Long pubId,
            @Valid @RequestBody UpdateAdminPubRequest request
    ) {
        adminBoothManagementService.updatePubManagement(pubId, request);
    }

    @PostMapping("/pub-operations")
    @Operation(summary = "주점 공통 운영정보 생성", description = "pub_operation 기준의 주점 공통 운영정보를 생성합니다.")
    public void createPubOperation(@Valid @RequestBody UpsertAdminPubOperationRequest request) {
        adminBoothManagementService.createPubOperation(request);
    }

    @PutMapping("/pub-operations/{pubOperationId}")
    @Operation(summary = "주점 공통 운영정보 수정", description = "기존 pub_operation 정보를 수정합니다.")
    public void updatePubOperation(
            @PathVariable Long pubOperationId,
            @Valid @RequestBody UpsertAdminPubOperationRequest request
    ) {
        adminBoothManagementService.updatePubOperation(pubOperationId, request);
    }

    @DeleteMapping("/pub-operations/{pubOperationId}")
    @Operation(summary = "주점 공통 운영정보 삭제", description = "기존 pub_operation 정보를 삭제합니다.")
    public void deletePubOperation(@PathVariable Long pubOperationId) {
        adminBoothManagementService.deletePubOperation(pubOperationId);
    }
}
