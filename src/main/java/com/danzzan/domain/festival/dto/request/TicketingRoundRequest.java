package com.danzzan.domain.festival.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class TicketingRoundRequest {

    @NotNull(message = "티켓팅 날짜와 시간을 입력해 주세요.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime ticketingAt;

    @Min(value = 1, message = "티켓 수량은 1개 이상이어야 합니다.")
    private int capacity;

    @NotNull(message = "공연 날짜를 선택해 주세요.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate performanceDate;
}
