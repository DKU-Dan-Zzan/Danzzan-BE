package com.danzzan.domain.timetable.dto.admin.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateArtistRequest {

    @NotBlank(message = "아티스트 이름을 입력해 주세요.")
    @Size(max = 255, message = "아티스트 이름은 255자 이내로 입력해 주세요.")
    private String name;

    private String description;

    /**
     * 신규 생성 시점에 이미지가 이미 업로드된 경우 함께 저장한다. (선택)
     */
    private String imageUrl;
}
