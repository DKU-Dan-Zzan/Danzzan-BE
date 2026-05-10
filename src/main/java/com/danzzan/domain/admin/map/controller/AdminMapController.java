package com.danzzan.domain.admin.map.controller;

import com.danzzan.domain.admin.map.dto.request.CreateAdminPubRequest;
import com.danzzan.domain.admin.map.dto.request.PresignAdminPubImageRequest;
import com.danzzan.domain.admin.map.dto.request.RegisterAdminPubImagesRequest;
import com.danzzan.domain.admin.map.dto.request.UpdateAdminBoothRequest;
import com.danzzan.domain.admin.map.dto.request.UpdateAdminPubRequest;
import com.danzzan.domain.admin.map.dto.request.UpdateMapLocationRequest;
import com.danzzan.domain.admin.map.dto.request.UpsertAdminPubOperationRequest;
import com.danzzan.domain.admin.map.dto.response.AdminBoothManagementResponse;
import com.danzzan.domain.admin.map.dto.response.AdminMapResponse;
import com.danzzan.domain.admin.map.dto.response.AdminPubImagePresignResponse;
import com.danzzan.domain.admin.map.dto.response.AdminPubImageResponse;
import com.danzzan.domain.admin.map.service.AdminBoothManagementService;
import com.danzzan.domain.admin.map.service.AdminMapService;
import com.danzzan.infra.s3.S3PresignedPutResult;
import com.danzzan.infra.s3.S3UploadResult;
import com.danzzan.infra.s3.S3Uploader;
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
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/map")
@Tag(name = "관리자 지도", description = "관리자 지도/Booth 관리 API")
@SecurityRequirement(name = "bearerAuth")
public class AdminMapController {

    private final AdminMapService adminMapService;
    private final AdminBoothManagementService adminBoothManagementService;
    private final S3Uploader s3Uploader;

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

    @PostMapping("/pubs")
    @Operation(summary = "주점 추가", description = "pub row와 pub_display_day row를 함께 생성합니다.")
    public Long createPubManagement(@Valid @RequestBody CreateAdminPubRequest request) {
        return adminBoothManagementService.createPubManagement(request);
    }

    @PatchMapping("/pubs/{pubId}/hide")
    @Operation(summary = "주점 숨김", description = "pub는 유지하고 pub_display_day row만 모두 제거합니다.")
    public void hidePubManagement(@PathVariable Long pubId) {
        adminBoothManagementService.hidePubManagement(pubId);
    }

    @GetMapping("/pubs/{pubId}/images")
    @Operation(summary = "주점 이미지 목록 조회", description = "특정 주점의 등록 이미지와 대표 이미지를 조회합니다.")
    public List<AdminPubImageResponse> getPubImages(@PathVariable Long pubId) {
        return adminBoothManagementService.getPubImages(pubId);
    }

    @PostMapping("/pubs/{pubId}/images/presign")
    @Operation(summary = "주점 이미지 업로드용 Presigned URL 발급", description = "주점 이미지를 Object Storage에 업로드하기 위한 presigned URL을 발급합니다.")
    public AdminPubImagePresignResponse presignPubImage(
            @PathVariable Long pubId,
            @Valid @RequestBody PresignAdminPubImageRequest request
    ) {
        S3PresignedPutResult result = adminBoothManagementService.presignPubImage(pubId, request);
        return AdminPubImagePresignResponse.from(result);
    }

    @PostMapping("/pubs/{pubId}/images/upload")
    @Operation(summary = "주점 이미지 직접 업로드", description = "주점 이미지를 서버를 통해 Object Storage에 직접 업로드합니다.")
    public Map<String, String> uploadPubImage(
            @PathVariable Long pubId,
            @RequestParam("file") MultipartFile file
    ) {
        S3UploadResult result = adminBoothManagementService.uploadPubImage(pubId, file);
        return Map.of("imageUrl", result.url(), "key", result.key());
    }

    @PostMapping("/pubs/{pubId}/images")
    @Operation(summary = "주점 이미지 등록", description = "업로드 완료된 이미지 URL들을 pub_image 테이블에 등록합니다.")
    public void registerPubImages(
            @PathVariable Long pubId,
            @Valid @RequestBody RegisterAdminPubImagesRequest request
    ) {
        adminBoothManagementService.registerPubImages(pubId, request);
    }

    @PatchMapping("/pubs/{pubId}/images/{imageId}/main")
    @Operation(summary = "주점 메인 이미지 지정", description = "특정 주점의 대표 이미지를 하나로 지정합니다.")
    public void updateMainPubImage(
            @PathVariable Long pubId,
            @PathVariable Long imageId
    ) {
        adminBoothManagementService.updateMainPubImage(pubId, imageId);
    }

    @DeleteMapping("/pubs/{pubId}/images/{imageId}")
    @Operation(summary = "주점 이미지 삭제", description = "pub_image 테이블의 이미지 row를 삭제합니다.")
    public void deletePubImage(
            @PathVariable Long pubId,
            @PathVariable Long imageId
    ) {
        adminBoothManagementService.deletePubImage(pubId, imageId);
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
