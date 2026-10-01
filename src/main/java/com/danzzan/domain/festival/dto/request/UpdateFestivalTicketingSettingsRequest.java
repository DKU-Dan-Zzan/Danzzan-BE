package com.danzzan.domain.festival.dto.request;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class UpdateFestivalTicketingSettingsRequest {
    @jakarta.validation.constraints.Size(max = 2048)
    @jakarta.validation.constraints.Pattern(regexp = "https?://[^\\s]+", message = "올바른 이미지 URL을 입력해 주세요.")
    private String ticketingBackgroundImageUrl;
    @com.fasterxml.jackson.annotation.JsonIgnore
    @lombok.Setter(lombok.AccessLevel.NONE)
    private boolean backgroundImageSpecified;

    public void setTicketingBackgroundImageUrl(String url) {
        this.ticketingBackgroundImageUrl = url;
        this.backgroundImageSpecified = true;
    }

    @jakarta.validation.constraints.Size(max = 2048)
    @jakarta.validation.constraints.Pattern(regexp = "https?://[^\\s]+", message = "올바른 이미지 URL을 입력해 주세요.")
    private String ticketCardBackgroundImageUrl;
    @com.fasterxml.jackson.annotation.JsonIgnore
    @lombok.Setter(lombok.AccessLevel.NONE)
    private boolean ticketCardBackgroundImageSpecified;

    public void setTicketCardBackgroundImageUrl(String url) {
        this.ticketCardBackgroundImageUrl = url;
        this.ticketCardBackgroundImageSpecified = true;
    }

    @NotNull(message = "티켓팅 사용 여부를 입력해 주세요.")
    private Boolean ticketingEnabled;
    @NotNull(message = "티켓팅 회차 목록을 입력해 주세요.")
    @Valid
    private List<@NotNull @Valid TicketingRoundRequest> ticketingRounds;
    @NotNull(message = "티켓 취소 확인 회차 목록을 입력해 주세요.")
    private List<Long> confirmedTicketCancelRoundIds = new ArrayList<>();
}
