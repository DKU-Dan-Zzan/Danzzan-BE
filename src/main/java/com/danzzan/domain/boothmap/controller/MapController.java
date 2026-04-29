package com.danzzan.domain.boothmap.controller;

import com.danzzan.domain.boothmap.model.dto.BoothMapResponse;
import com.danzzan.domain.boothmap.model.dto.BoothSummaryResponse;
import com.danzzan.domain.boothmap.model.dto.PubDetailResponse;
import com.danzzan.domain.boothmap.model.dto.PubSummaryResponse;
import com.danzzan.domain.boothmap.service.BoothMapService;
import com.danzzan.domain.boothmap.service.BoothService;
import com.danzzan.domain.boothmap.service.PubService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
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
            description = "지도에 표시할 단과대와 부스 위치 정보를 조회합니다."
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
                                                  "name": "화장실",
                                                  "type": "FACILITY",
                                                  "subType": "TOILET",
                                                  "locationX": 210.2,
                                                  "locationY": 95.1,
                                                  "startTime": "10:00",
                                                  "endTime": "22:00"
                                                }
                                              ]
                                            }
                                            """
                            )
                    )
            )
    })
    public BoothMapResponse getBoothMap(
            @Parameter(description = "조회할 축제 날짜", example = "2026-05-20")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return boothMapService.getBoothMap(date);
    }

    @GetMapping("/booths/{boothId}")
    @Operation(
            summary = "부스 요약 정보 조회",
            description = "부스 클릭 시 표시할 요약 정보를 조회합니다."
    )
    public BoothSummaryResponse getBoothSummary(
            @NotNull @PathVariable Long boothId,
            @Parameter(description = "조회할 축제 날짜", example = "2026-05-20")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return boothService.getBoothSummary(boothId, date);
    }

    @GetMapping("/pubs")
    @Operation(
            summary = "학과 주점 전체 목록 조회",
            description = "학과 주점 목록을 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "학과 주점 전체 목록 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = PubSummaryResponse.class))
                    )
            )
    })
    public List<PubSummaryResponse> getPubs(
            @Parameter(description = "조회할 축제 날짜", example = "2026-05-20")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return pubService.getPubs(date);
    }

    @GetMapping("/pubs/{pubId}")
    @Operation(
            summary = "학과 주점 상세 조회",
            description = "특정 학과 주점의 상세 정보를 조회합니다."
    )
    public PubDetailResponse getPubDetail(
            @NotNull @PathVariable Long pubId,
            @Parameter(description = "조회할 축제 날짜", example = "2026-05-20")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return pubService.getPubDetail(pubId, date);
    }
}
