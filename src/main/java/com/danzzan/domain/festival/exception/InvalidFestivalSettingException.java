package com.danzzan.domain.festival.exception;

/**
 * 축제 설정 값이 서로 맞지 않을 때(예: 종료일이 시작일보다 빠름) 발생한다.
 *
 * IllegalArgumentException 을 쓰지 않는 이유: 이 프로젝트의 전역 핸들러는
 * IllegalArgumentException 을 404(찾을 수 없음)로 내보낸다. 입력값 문제는 400 이라
 * 별도 예외를 둔다.
 */
public class InvalidFestivalSettingException extends RuntimeException {
    public InvalidFestivalSettingException(String message) {
        super(message);
    }
}
