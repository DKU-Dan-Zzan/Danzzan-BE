package com.danzzan.domain.festival.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class UpdateFestivalSettingRequest {

    @NotBlank(message = "학교명을 입력해 주세요.")
    private String schoolName;

    @NotBlank(message = "축제 이름을 입력해 주세요.")
    private String festivalName;

    @NotNull(message = "운영 시작일을 선택해 주세요.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @NotNull(message = "운영 종료일을 선택해 주세요.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    private boolean ticketingEnabled;

    /** 티켓팅을 쓰지 않으면 비워 둔다. 보낸 목록이 저장된 회차를 통째로 대신한다. */
    @Valid
    private List<TicketingRoundRequest> ticketingRounds = new ArrayList<>();

    /**
     * 이미 티켓이 나간 회차를 지울 때, 그 회차 id 를 여기에 담는다.
     *
     * 관리자가 "발급된 티켓도 함께 취소" 를 확인한 회차만 들어온다. 목록에 없으면
     * 서버가 삭제를 거절한다. 실수로 학생 티켓이 사라지는 일을 막기 위한 장치다.
     */
    private List<Long> confirmedTicketCancelRoundIds = new ArrayList<>();
}
