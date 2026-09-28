package com.danzzan.domain.festival.controller;

import com.danzzan.domain.festival.dto.response.FestivalSettingResponse;
import com.danzzan.domain.festival.service.FestivalSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/festival")
@Tag(name = "축제 설정", description = "축제 운영 정보 조회 API")
public class FestivalSettingController {

    private final FestivalSettingService festivalSettingService;

    @GetMapping("/settings")
    @Operation(
            summary = "축제 운영 정보 조회",
            description = "축제 이름과 운영 날짜, 티켓팅 회차를 조회합니다. 부스맵·타임테이블의 날짜 탭이 이 값을 씁니다."
    )
    public FestivalSettingResponse getSettings() {
        return festivalSettingService.getSettings();
    }
}
