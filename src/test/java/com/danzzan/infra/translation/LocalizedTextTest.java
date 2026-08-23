package com.danzzan.infra.translation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalizedTextTest {

    @Test
    void 영문이_null이면_한국어로_폴백한다() {
        String result = LocalizedText.pick(true, "한국어", null);

        assertEquals("한국어", result);
    }

    @Test
    void 영문이_공백뿐이면_한국어로_폴백한다() {
        String result = LocalizedText.pick(true, "한국어", "   ");

        assertEquals("한국어", result);
    }

    @Test
    void 영문이_있으면_영문을_반환한다() {
        String result = LocalizedText.pick(true, "한국어", "English");

        assertEquals("English", result);
    }

    @Test
    void 한국어_요청이면_영문이_있어도_한국어를_반환한다() {
        String result = LocalizedText.pick(false, "한국어", "English");

        assertEquals("한국어", result);
    }
}
