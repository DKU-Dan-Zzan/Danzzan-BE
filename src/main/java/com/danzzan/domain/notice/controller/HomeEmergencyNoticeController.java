package com.danzzan.domain.notice.controller;

import com.danzzan.domain.notice.dto.response.HomeEmergencyNoticeDto;
import com.danzzan.domain.notice.service.HomeEmergencyNoticeQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/home")
public class HomeEmergencyNoticeController {

    private final HomeEmergencyNoticeQueryService homeEmergencyNoticeQueryService;

    @GetMapping("/emergencyNotice")
    public HomeEmergencyNoticeDto getActiveEmergencyNotice() {
        return homeEmergencyNoticeQueryService.getActiveEmergencyNotice();
    }
}
