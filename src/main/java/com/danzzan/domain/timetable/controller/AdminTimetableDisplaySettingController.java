package com.danzzan.domain.timetable.controller;

import com.danzzan.domain.timetable.dto.request.UpdateComingSoonOverlayRequest;
import com.danzzan.domain.timetable.service.TimetableDisplaySettingService;
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
@RequestMapping("/admin/timetable/display-settings")
@Tag(name = "관리자 타임테이블 설정", description = "관리자 타임테이블 노출 설정 API")
@SecurityRequirement(name = "bearerAuth")
public class AdminTimetableDisplaySettingController {

    private final TimetableDisplaySettingService timetableDisplaySettingService;

    @PutMapping("/coming-soon-overlay")
    @Operation(
            summary = "타임테이블 Coming Soon 오버레이 노출 설정",
            description = "타임테이블 화면에 반투명 Coming Soon 오버레이를 노출할지 설정합니다."
    )
    public void updateComingSoonOverlay(
            @RequestBody @Valid UpdateComingSoonOverlayRequest request
    ) {
        timetableDisplaySettingService.updateComingSoonOverlayEnabled(request.isComingSoonOverlayEnabled());
    }
}
