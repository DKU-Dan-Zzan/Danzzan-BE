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
}
