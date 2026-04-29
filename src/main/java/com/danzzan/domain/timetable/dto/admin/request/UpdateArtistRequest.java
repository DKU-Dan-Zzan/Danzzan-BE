package com.danzzan.domain.timetable.dto.admin.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 아티스트 수정 요청. 모든 필드는 선택적이며 null이면 변경하지 않는다.
 * imageUrl은 명시적으로 빈 문자열을 보내면 이미지 제거로 처리된다.
 */
@Getter
@Setter
@NoArgsConstructor
public class UpdateArtistRequest {

    @Size(max = 255, message = "아티스트 이름은 255자 이내로 입력해 주세요.")
    private String name;

    private String description;

    private String imageUrl;
}
