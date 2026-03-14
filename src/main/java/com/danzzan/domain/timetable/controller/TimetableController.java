package com.danzzan.domain.timetable.controller;

import com.danzzan.domain.timetable.model.dto.ContentImageDto;
import com.danzzan.domain.timetable.model.dto.TimetableResponseDto;
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
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/timetable")
@Tag(name = "타임테이블", description = "공연 타임테이블 및 콘텐츠 이미지 조회 API")
public class TimetableController {

    private final TimetableService timetableService;

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
                                                  "artistName": "홍길동",
                                                  "artistImageUrl": "https://cdn.example.com/artist1.jpg",
                                                  "artistDescription": "감성 록밴드"
                                                },
                                                {
                                                  "performanceId": 2,
                                                  "startTime": "19:00",
                                                  "endTime": "19:40",
                                                  "stage": "MAIN_STAGE",
                                                  "artistId": 11,
                                                  "artistName": "김철수",
                                                  "artistImageUrl": "https://cdn.example.com/artist2.jpg",
                                                  "artistDescription": "싱어송라이터"
                                                }
                                              ]
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 날짜 형식 요청",
                    content = @Content(
                        mediaType = "application/json",
                        examples = @ExampleObject(
                            name = "badRequest",
                            value = """
                                    {
                                    "message": "요청 파라미터 'date' 형식이 올바르지 않습니다.",
                                    "status": 400
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
            LocalDate date
    ) {
        return timetableService.getPerformances(date);
    }

    @GetMapping("/content-images")
    @Operation(
            summary = "타임테이블 콘텐츠 이미지 조회",
            description = "타임테이블 화면에서 사용할 콘텐츠 이미지 목록을 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "콘텐츠 이미지 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = ContentImageDto.class)),
                            examples = @ExampleObject(
                                    name = "success",
                                    value = """
                                            [
                                              {
                                                "id": 1,
                                                "name": "땅따먹기",
                                                "previewImageUrl": "https://cdn.example.com/content-preview-1.jpg",
                                                "detailImageUrl": "https://cdn.example.com/content-detail-1.jpg"
                                              },
                                              {
                                                "id": 2,
                                                "name": "무궁화 꽃이 피었습니다",
                                                "previewImageUrl": "https://cdn.example.com/content-preview-2.jpg",
                                                "detailImageUrl": "https://cdn.example.com/content-detail-2.jpg"
                                              }
                                            ]
                                            """
                            )
                    )
            )
    })
    public List<ContentImageDto> getContentImages() {
        return timetableService.getContentImages();
    }
}