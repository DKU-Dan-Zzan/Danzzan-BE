package com.danzzan.domain.timetable.controller;

import com.danzzan.domain.timetable.model.dto.ContentImageDto;
import com.danzzan.domain.timetable.model.dto.TimetableDisplayConfigResponse;
import com.danzzan.domain.timetable.model.dto.TimetableResponseDto;
import com.danzzan.domain.timetable.service.TimetableDisplaySettingService;
import com.danzzan.domain.timetable.service.TimetableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/timetable")
@Tag(name = "타임테이블", description = "공연 타임테이블 및 콘텐츠 이미지 조회 API")
public class TimetableController {

    private final TimetableService timetableService;
    private final TimetableDisplaySettingService timetableDisplaySettingService;

    @GetMapping("/performances")
    @Operation(
            summary = "날짜별 공연 타임테이블 조회",
            description = "선택한 날짜에 해당하는 공연 타임테이블 정보를 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "공연 타임테이블 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TimetableResponseDto.class),
                            examples = @ExampleObject(
                                    name = "success",
                                    value = """
                                            {
                                              "date": "2026-05-13",
                                              "performances": [
                                                {
                                                  "performanceId": 1,
                                                  "startTime": "18:00",
                                                  "endTime": "18:30",
                                                  "stage": "MAIN_STAGE",
                                                  "artistId": 10,
                                                  "artistName": "잔나비",
                                                  "artistImageUrl": "https://cdn.example.com/artist1.jpg",
                                                  "artistDescription": "감성 록밴드"
                                                }
                                              ]
                                            }
                                            """
                            )
                    )
            )
    })
    public TimetableResponseDto getPerformances(
            @Parameter(
                    description = "조회할 공연 날짜 (yyyy-MM-dd 형식)",
                    example = "2026-05-13"
            )
            @RequestParam("date")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date,
            @Parameter(description = "표시 언어 (ko|en)", example = "en")
            @RequestParam(name = "lang", required = false, defaultValue = "ko") String lang
    ) {
        return timetableService.getPerformances(date, "en".equalsIgnoreCase(lang));
    }

    @GetMapping("/content-images")
    @Operation(
            summary = "타임테이블 콘텐츠 이미지 조회",
            description = "타임테이블 화면에서 사용하는 콘텐츠 이미지 목록을 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "콘텐츠 이미지 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = ContentImageDto.class))
                    )
            )
    })
    public List<ContentImageDto> getContentImages() {
        return timetableService.getContentImages();
    }

    @GetMapping("/display-config")
    @Operation(
            summary = "타임테이블 화면 노출 설정 조회",
            description = "타임테이블 Coming Soon 오버레이 노출 여부를 조회합니다."
    )
    public TimetableDisplayConfigResponse getDisplayConfig() {
        return new TimetableDisplayConfigResponse(
                timetableDisplaySettingService.isComingSoonOverlayEnabled()
        );
    }
}
