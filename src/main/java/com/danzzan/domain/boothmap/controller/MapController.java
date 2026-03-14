package com.danzzan.domain.boothmap.controller;

import com.danzzan.domain.boothmap.model.dto.BoothMapResponse;
import com.danzzan.domain.boothmap.model.dto.BoothSummaryResponse;
import com.danzzan.domain.boothmap.model.dto.PubDetailResponse;
import com.danzzan.domain.boothmap.model.dto.PubSummaryResponse;
import com.danzzan.domain.boothmap.service.BoothMapService;
import com.danzzan.domain.boothmap.service.BoothService;
import com.danzzan.domain.boothmap.service.PubService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/map")
@Tag(name = "부스맵", description = "부스맵 조회 API")
public class MapController {
    private final BoothMapService boothMapService;
    private final BoothService boothService;
    private final PubService pubService;

    @GetMapping("/booth-map")
    @Operation(
            summary = "부스맵 전체 조회",
            description = "지도에 표시할 단과대 및 부스 위치 정보를 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "부스맵 전체 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BoothMapResponse.class),
                            examples = @ExampleObject(
                                    name = "success",
                                    value = """
                                            {
                                              "colleges": [
                                                {
                                                  "collegeId": 1,
                                                  "name": "SW융합대학",
                                                  "locationX": 120.5,
                                                  "locationY": 88.3
                                                }
                                              ],
                                              "booths": [
                                                {
                                                  "boothId": 3,
                                                  "name": "심폐소생술 체험",
                                                  "type": "EXPERIENCE",
                                                  "locationX": 210.2,
                                                  "locationY": 95.1
                                                }
                                              ]
                                            }
                                            """
                            )
                    )
            )
    })
    public BoothMapResponse getBoothMap() {
        return boothMapService.getBoothMap();
    }

    @GetMapping("/booths/{boothId}")
    @Operation(
            summary = "부스 요약 정보 조회",
            description = "부스 클릭 시 표시할 요약 정보를 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "부스 요약 정보 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BoothSummaryResponse.class),
                            examples = @ExampleObject(
                                    name = "success",
                                    value = """
                                            {
                                              "boothId": 3,
                                              "name": "심폐소생술 체험",
                                              "description": "응급상황 대처 체험 부스",
                                              "imageUrl": "https://cdn.example.com/booth3.jpg"
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "해당 부스(boothId)가 존재하지 않음"
            )
    })
    public BoothSummaryResponse getBoothSummary(@NotNull @PathVariable Long boothId) {
        return boothService.getBoothSummary(boothId);
    }

    @GetMapping("/pubs")
    @Operation(
            summary = "학과 주점 전체 리스트 조회",
            description = "학과 주점 목록을 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "학과 주점 전체 리스트 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = PubSummaryResponse.class)),
                            examples = @ExampleObject(
                                    name = "success",
                                    value = """
                                            [
                                              {
                                                "pubId": 1,
                                                "name": "소프트웨어학과 주점",
                                                "intro": "오늘만 사는 주점",
                                                "department": "소프트웨어학과",
                                                "collegeName": "SW융합대학",
                                                "mainImageUrl": "https://image.url/main.png"
                                              },
                                              {
                                                "pubId": 2,
                                                "name": "전자공학과 주점",
                                                "intro": "신나는 주점",
                                                "department": "전자공학과",
                                                "collegeName": "공과대학",
                                                "mainImageUrl": "https://image.url/main2.png"
                                              }
                                            ]
                                            """
                            )
                    )
            )
    })
    public List<PubSummaryResponse> getPubs() {
        return pubService.getPubs();
    }

    @GetMapping("/pubs/{pubId}")
    @Operation(
            summary = "학과 주점 상세 조회",
            description = "특정 학과 주점의 상세 정보를 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "학과 주점 상세 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PubDetailResponse.class),
                            examples = @ExampleObject(
                                    name = "success",
                                    value = """
                                            {
                                              "pubId": 1,
                                              "name": "소프트웨어학과 주점",
                                              "intro": "오늘만 사는 주점",
                                              "description": "시원한 맥주와 안주가 준비되어 있습니다.",
                                              "department": "소프트웨어학과",
                                              "collegeName": "SW융합대학",
                                              "instagram": "@dku_software",
                                              "imageUrls": [
                                                "https://cdn.xxx/pub1_main.jpg",
                                                "https://cdn.xxx/pub1_2.jpg"
                                              ]
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "해당 주점(pubId)이 존재하지 않음"
            )
    })
    public PubDetailResponse getPubDetail(@NotNull @PathVariable Long pubId) {
        return pubService.getPubDetail(pubId);
    }
}