package com.danzzan.domain.festival.dto.response;

import com.danzzan.domain.festival.entity.FestivalSetting;

import java.time.LocalDate;
import java.util.List;

/**
 * 축제 설정 조회 응답.
 *
 * operationDates 는 시작일~종료일을 하루씩 펼친 목록이다. 프론트가 날짜 탭을 그릴 때
 * 이 목록을 그대로 쓰도록 서버가 계산해서 내려준다.
 */
public record FestivalSettingResponse(
        String schoolName,
        String festivalName,
        LocalDate startDate,
        LocalDate endDate,
        List<LocalDate> operationDates,
        boolean ticketingEnabled,
        List<TicketingRoundResponse> ticketingRounds,
        String ticketingBackgroundImageUrl,
        String ticketingOpenBackgroundImageUrl
) {

    public static FestivalSettingResponse of(
            FestivalSetting setting,
            List<LocalDate> operationDates,
            List<TicketingRoundResponse> rounds
    ) {
        return new FestivalSettingResponse(
                setting.getSchoolName(),
                setting.getFestivalName(),
                setting.getStartDate(),
                setting.getEndDate(),
                operationDates,
                setting.isTicketingEnabled(),
                rounds,
                setting.getTicketingBackgroundImageUrl(),
                setting.getTicketingOpenBackgroundImageUrl()
        );
    }
}
