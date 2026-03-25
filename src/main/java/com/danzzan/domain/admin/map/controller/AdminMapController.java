package com.danzzan.domain.admin.map.controller;

import com.danzzan.domain.admin.map.dto.request.UpdateMapLocationRequest;
import com.danzzan.domain.admin.map.dto.request.UpdateActiveOperationDateRequest;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;

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
            description = "선택한 날짜 기준으로 단과대/부스 배치 현황과 현재 활성 날짜를 조회합니다."
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
                                              "activeOperationDate": "2026-05-12",
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
                    description = "조회할 운영 날짜. 없으면 현재 활성 날짜로 조회합니다.",
                    example = "2026-05-12"
            )
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        LocalDate operationDate = (date == null)
                ? adminMapService.getActiveOperationDate()
                : date;

        return adminMapService.getAdminMap(operationDate);
    }

    @PatchMapping("/colleges/{collegeId}/location")
    @Operation(
            summary = "단과대 마커 위치 수정",
            description = "관리자 지도에서 특정 단과대 마커의 좌표를 수정합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "단과대 위치 수정 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "좌표 값이 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "해당 단과대가 존재하지 않음"
            )
    })
    public void updateCollegeLocation(
            @Parameter(description = "위치를 수정할 단과대 ID", example = "1")
            @PathVariable Long collegeId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UpdateMapLocationRequest.class),
                            examples = @ExampleObject(
                                    name = "request",
                                    value = """
                                            {
                                              "locationX": 127.1265,
                                              "locationY": 37.3211
                                            }
                                            """
                            )
                    )
            )
            @Valid @RequestBody UpdateMapLocationRequest request
    ) {
        adminMapService.updateCollegeLocation(collegeId, request);
    }

    @PatchMapping("/booths/{boothId}/location")
    @Operation(
            summary = "부스 마커 위치 수정",
            description = "관리자 지도에서 특정 부스 마커의 좌표를 수정합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "부스 위치 수정 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "좌표 값이 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "해당 부스가 존재하지 않음"
            )
    })
    public void updateBoothLocation(
            @Parameter(description = "위치를 수정할 부스 ID", example = "3")
            @PathVariable Long boothId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UpdateMapLocationRequest.class),
                            examples = @ExampleObject(
                                    name = "request",
                                    value = """
                                            {
                                              "locationX": 127.1271,
                                              "locationY": 37.3219
                                            }
                                            """
                            )
                    )
            )
            @Valid @RequestBody UpdateMapLocationRequest request
    ) {
        adminMapService.updateBoothLocation(boothId, request);
    }

    @DeleteMapping("/booths/{boothId}/location")
    @Operation(
            summary = "부스 마커 위치 삭제",
            description = "특정 부스의 배치 좌표를 제거하여 미배치 상태로 되돌립니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "부스 위치 삭제 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "해당 부스가 존재하지 않음"
            )
    })
    public void clearBoothLocation(
            @Parameter(description = "위치를 삭제할 부스 ID", example = "3")
            @PathVariable Long boothId
    ) {
        adminMapService.clearBoothLocation(boothId);
    }

    @PutMapping("/active-date")
    @Operation(
            summary = "활성 운영 날짜 변경",
            description = "부스맵 기본 조회에 사용되는 활성 운영 날짜를 변경합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "활성 운영 날짜 변경 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "날짜 형식이 올바르지 않음"
            )
    })
    public void updateActiveDate(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UpdateActiveOperationDateRequest.class),
                            examples = @ExampleObject(
                                    name = "request",
                                    value = """
                                            {
                                              "operationDate": "2026-05-12"
                                            }
                                            """
                            )
                    )
            )
            @RequestBody UpdateActiveOperationDateRequest request
    ) {
        adminMapService.updateActiveDate(request);
    }
}
