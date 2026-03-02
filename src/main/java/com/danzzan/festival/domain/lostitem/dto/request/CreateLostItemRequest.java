package com.danzzan.festival.domain.lostitem.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class CreateLostItemRequest {

    @NotBlank(message = "물건 이름을 입력해 주세요.")
    private String itemName;

    private String imageUrl;

    @NotBlank(message = "습득 장소를 입력해 주세요.")
    private String foundLocation;

    @NotNull(message = "습득 날짜를 입력해 주세요.")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate foundDate;

    private Boolean isClaimed = false;

    private String receiverName;

    private String receiverNote;
}

