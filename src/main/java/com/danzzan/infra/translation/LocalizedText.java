package com.danzzan.infra.translation;

/**
 * 표시 언어에 맞는 문자열을 고른다.
 * 영문이 비어 있으면 한국어로 폴백한다. 사용자에게 빈 값이 보여서는 안 된다.
 */
public final class LocalizedText {

    private LocalizedText() {
    }

    public static String pick(boolean english, String korean, String translated) {
        if (english && translated != null && !translated.isBlank()) {
            return translated;
        }
        return korean;
    }
}
