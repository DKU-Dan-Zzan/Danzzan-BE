package com.danzzan.domain.festival.controller;

import com.danzzan.domain.festival.dto.request.UpdateFestivalSettingRequest;
import com.danzzan.domain.festival.dto.response.FestivalSettingResponse;
import com.danzzan.domain.festival.service.FestivalSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/festival")
@Tag(name = "관리자 축제 설정", description = "관리자 축제 운영 정보 API")
@SecurityRequirement(name = "bearerAuth")
public class AdminFestivalSettingController {

    private final FestivalSettingService festivalSettingService;

    @PutMapping("/settings")
    @Operation(
            summary = "축제 운영 정보 저장",
            description = "축제 이름/운영 날짜/티켓팅 회차를 저장합니다. 보낸 티켓팅 회차가 저장된 회차를 대신합니다."
    )
    public FestivalSettingResponse updateSettings(
            @RequestBody @Valid UpdateFestivalSettingRequest request
    ) {
        return festivalSettingService.updateSettings(request);
    }
}
