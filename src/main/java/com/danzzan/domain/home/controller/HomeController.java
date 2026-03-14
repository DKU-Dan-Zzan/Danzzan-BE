package com.danzzan.domain.home.controller;

import com.danzzan.domain.home.model.dto.HomeImageDto;
import com.danzzan.domain.home.model.dto.LineupImageDto;
import com.danzzan.domain.home.service.HomeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/home")
@Tag(name = "홈", description = "홈 화면 이미지 및 라인업 조회 API")
public class HomeController {

    private final HomeService homeService;

    @GetMapping("/images")
    @Operation(
            summary = "홈 이미지 조회",
            description = "홈 화면에 표시할 이미지를 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "홈 이미지 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = HomeImageDto.class)),
                            examples = @ExampleObject(
                                    name = "success",
                                    value = """
                                            [
                                              {
                                                "id": 1,
                                                "imageUrl": "https://cdn.example.com/home1.jpg"
                                              }
                                            ]
                                            """
                            )
                    )
            )
    })
    public List<HomeImageDto> getHomeImages() {
        return homeService.getHomeImages();
    }

    @GetMapping("/lineup-images")
    @Operation(
            summary = "라인업 이미지 조회",
            description = "홈 화면에 표시할 라인업 이미지 목록을 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "라인업 이미지 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = LineupImageDto.class)),
                            examples = @ExampleObject(
                                    name = "success",
                                    value = """
                                            [
                                              {
                                                "id": 1,
                                                "imageUrl": "https://cdn.example.com/lineup1.jpg"
                                              },
                                              {
                                                "id": 2,
                                                "imageUrl": "https://cdn.example.com/lineup2.jpg"
                                              }
                                            ]
                                            """
                            )
                    )
            )
    })
    public List<LineupImageDto> getLineupImages() {
        return homeService.getLineupImages();
    }
}