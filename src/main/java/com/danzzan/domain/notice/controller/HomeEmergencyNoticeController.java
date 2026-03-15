package com.danzzan.domain.notice.controller;

import com.danzzan.domain.notice.dto.response.HomeEmergencyNoticeDto;
import com.danzzan.domain.notice.service.HomeEmergencyNoticeQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/home")
@Tag(name = "홈 긴급공지", description = "홈 화면 긴급공지 조회 API")
public class HomeEmergencyNoticeController {

    private final HomeEmergencyNoticeQueryService homeEmergencyNoticeQueryService;

    @GetMapping("/emergencyNotice")
    @Operation(
            summary = "홈 긴급공지 조회",
            description = "홈 화면에 노출할 현재 활성화된 긴급공지 정보를 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "활성 긴급공지가 존재하는 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = HomeEmergencyNoticeDto.class),
                            examples = {
                                    @ExampleObject(
                                            name = "success",
                                            value = """
                                                    {
                                                      "id": 1,
                                                      "content": "우천 시 일부 공연이 지연될 수 있습니다.",
                                                      "updatedAt": "52분전"
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "204",
                    description = "활성화된 긴급공지가 없는 경우 (본문 없음)"
            )
    })
    public ResponseEntity<HomeEmergencyNoticeDto> getActiveEmergencyNotice() {
        HomeEmergencyNoticeDto activeEmergencyNotice = homeEmergencyNoticeQueryService.getActiveEmergencyNotice();

        if (activeEmergencyNotice == null) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(activeEmergencyNotice);
    }
}