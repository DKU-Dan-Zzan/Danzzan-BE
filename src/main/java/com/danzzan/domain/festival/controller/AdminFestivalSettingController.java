package com.danzzan.domain.festival.controller;

import com.danzzan.domain.festival.dto.request.UpdateFestivalSettingRequest;
import com.danzzan.domain.festival.dto.request.UpdateFestivalTicketingSettingsRequest;
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
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/festival")
@Tag(name = "관리자 축제 설정", description = "관리자 축제 운영 정보 API")
@SecurityRequirement(name = "bearerAuth")
public class AdminFestivalSettingController {

    private final FestivalSettingService festivalSettingService;
    private final com.danzzan.domain.festival.service.TicketingBackgroundService ticketingBackgroundService;

    @org.springframework.web.bind.annotation.PostMapping(value = "/ticketing-background", consumes = "multipart/form-data")
    @PreAuthorize("@userAdminAuthorizationService.hasTicketingRole(authentication)")
    public com.danzzan.infra.s3.S3UploadResult uploadBackground(
            @org.springframework.web.bind.annotation.RequestPart("file") org.springframework.web.multipart.MultipartFile file) {
        return ticketingBackgroundService.upload(file);
    }

    @PutMapping("/settings")
    @PreAuthorize("@userAdminAuthorizationService.hasOperationsRole(authentication)")
    @Operation(
            summary = "축제 운영 정보 저장",
            description = "축제 이름과 운영 날짜를 저장합니다. 티켓팅 설정은 별도 API에서 저장합니다."
    )
    public FestivalSettingResponse updateSettings(
            @RequestBody @Valid UpdateFestivalSettingRequest request
    ) {
        return festivalSettingService.updateMetadata(request);
    }

    @PutMapping("/ticketing-settings")
    @PreAuthorize("@userAdminAuthorizationService.hasTicketingRole(authentication)")
    public FestivalSettingResponse updateTicketingSettings(
            @RequestBody @Valid UpdateFestivalTicketingSettingsRequest request
    ) {
        return festivalSettingService.updateTicketingSettings(request);
    }
}
