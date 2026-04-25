package com.danzzan.domain.admin.map.controller;

import com.danzzan.domain.admin.map.dto.request.UpdateMapLocationRequest;
import com.danzzan.domain.admin.map.dto.response.AdminMapResponse;
import com.danzzan.domain.admin.map.service.AdminMapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/map")
@Tag(name = "관리자 지도", description = "관리자 지도 편집 API")
@SecurityRequirement(name = "bearerAuth")
public class AdminMapController {

    private final AdminMapService adminMapService;

    @GetMapping
    @Operation(
            summary = "관리자 지도 조회",
            description = "선택한 운영 날짜 기준으로 단과대와 부스 배치 현황을 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "관리자 지도 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminMapResponse.class),
                            examples = @ExampleObject(
                                    name = "success",
                                    value = """
                                            {
                                              "comingSoonOverlayEnabled": true,
                                              "colleges": [
                                                {
                                                  "id": 1,
                                                  "name": "SW융합대학",
                                                  "locationX": 127.1265,
                                                  "locationY": 37.3211
                                                }
                                              ],
                                              "booths": [
                                                {
                                                  "id": 3,
                                                  "name": "화장실",
                                                  "type": "FACILITY",
                                                  "locationX": 127.1271,
                                                  "locationY": 37.3219,
                                                  "placed": true
                                                }
                                              ]
                                            }
                                            """
                            )
                    )
            )
    })
    public AdminMapResponse getAdminMap(
            @Parameter(
                    description = "조회할 운영 날짜. 없으면 가장 이른 부스 운영 날짜를 사용합니다.",
                    example = "2026-05-12"
            )
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return adminMapService.getAdminMap(date);
    }

    @PatchMapping("/colleges/{collegeId}/location")
    @Operation(
            summary = "단과대 마커 위치 수정",
            description = "관리자 지도에서 특정 단과대 마커 위치를 수정합니다."
    )
    public void updateCollegeLocation(
            @Parameter(description = "위치를 수정할 단과대 ID", example = "1")
            @PathVariable Long collegeId,
            @Valid @RequestBody UpdateMapLocationRequest request
    ) {
        adminMapService.updateCollegeLocation(collegeId, request);
    }

    @PatchMapping("/booths/{boothId}/location")
    @Operation(
            summary = "부스 마커 위치 수정",
            description = "관리자 지도에서 특정 부스 마커 위치를 수정합니다."
    )
    public void updateBoothLocation(
            @Parameter(description = "위치를 수정할 부스 ID", example = "3")
            @PathVariable Long boothId,
            @Valid @RequestBody UpdateMapLocationRequest request
    ) {
        adminMapService.updateBoothLocation(boothId, request);
    }

    @DeleteMapping("/booths/{boothId}/location")
    @Operation(
            summary = "부스 마커 위치 제거",
            description = "특정 부스의 배치 좌표를 제거합니다."
    )
    public void clearBoothLocation(
            @Parameter(description = "위치를 제거할 부스 ID", example = "3")
            @PathVariable Long boothId
    ) {
        adminMapService.clearBoothLocation(boothId);
    }
}
