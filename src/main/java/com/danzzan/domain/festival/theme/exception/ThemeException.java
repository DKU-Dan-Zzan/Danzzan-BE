package com.danzzan.domain.festival.theme.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ThemeException extends RuntimeException {
    private final HttpStatus status;

    private ThemeException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static ThemeException settingNotFound() {
        return new ThemeException(HttpStatus.NOT_FOUND, "축제 설정을 찾을 수 없습니다.");
    }

    public static ThemeException themeNotFound() {
        return new ThemeException(HttpStatus.NOT_FOUND, "테마를 찾을 수 없습니다.");
    }

    public static ThemeException wrongFestival() {
        return new ThemeException(HttpStatus.BAD_REQUEST, "해당 축제에 속한 테마만 활성화할 수 있습니다.");
    }

    public static ThemeException activeThemeDeletion() {
        return new ThemeException(HttpStatus.CONFLICT, "현재 적용 중인 테마는 삭제할 수 없습니다.");
    }
}
