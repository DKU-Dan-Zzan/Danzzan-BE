package com.danzzan.infra.translation;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link FieldTranslationDecision#decideEnglish}를 순수 함수 진리표로 검증한다.
 * 네 입력(koreanChanged, suppliedEnglish, storedEnglish, autoEnglish)의 조합별
 * 최종 저장값을 명시적으로 고정해 둔다.
 */
class FieldTranslationDecisionTest {

    @Nested
    class 저장된_수동_영문이_있는_경우 {
        // 과제에서 요구한 기본 진리표: 필드에 이미 관리자가 입력해 둔 영문이 저장돼 있는 상태.

        @Test
        void 한국어가_바뀌고_영문도_새로_입력하면_새로_입력한_값이_이긴다() {
            String result = FieldTranslationDecision.decideEnglish(
                    true, "Manual X", "Old Manual", "Auto Y");

            assertEquals("Manual X", result);
        }

        @Test
        void 한국어는_안바뀌어도_영문을_새로_입력하면_새로_입력한_값이_이긴다() {
            String result = FieldTranslationDecision.decideEnglish(
                    false, "Manual X", "Old Manual", null);

            assertEquals("Manual X", result);
        }

        @Test
        void 한국어가_바뀌고_영문을_비워두면_새로_번역한_값으로_채워진다() {
            String result = FieldTranslationDecision.decideEnglish(
                    true, null, "Old Manual", "Fresh Auto");

            assertEquals("Fresh Auto", result);
        }

        @Test
        void 한국어도_안바뀌고_영문도_비워두면_저장된_값이_그대로_유지된다() {
            String result = FieldTranslationDecision.decideEnglish(
                    false, null, "Old Manual", null);

            assertEquals("Old Manual", result);
        }

        @Test
        void 한국어도_안바뀌고_영문도_비워두면_자동번역_결과가_와도_저장된_값이_그대로_유지된다() {
            // koreanChanged=false이므로 지우기 단계가 실행되지 않고, 값이 이미 채워져
            // 있으므로(non-null) 자동 채움 단계도 실행되지 않는다. autoEnglish가 우연히
            // 다른 필드 변경으로 인해 넘어오더라도 이 필드는 영향받지 않아야 한다.
            String result = FieldTranslationDecision.decideEnglish(
                    false, null, "Old Manual", "Unrelated Fresh Translation");

            assertEquals("Old Manual", result);
        }

        @Test
        void 공백만_있는_영문_입력은_비워둔_것과_동일하게_취급된다() {
            String result = FieldTranslationDecision.decideEnglish(
                    true, "   ", "Old Manual", "Auto W");

            assertEquals("Auto W", result);
        }
    }

    @Nested
    class 번역이_실패한_경우 {
        // autoEnglish가 null인, 이 로직이 가장 취약했던 지점: 지운 뒤 자동번역마저
        // 실패하면 값은 (예전 값으로 되돌아가지 않고) 진짜로 비워진 채 남아야 한다.

        @Test
        void 한국어가_바뀌고_영문도_비웠는데_번역까지_실패하면_값은_지워진_채로_남는다() {
            String result = FieldTranslationDecision.decideEnglish(
                    true, null, "Old Manual", null);

            assertNull(result);
        }

        @Test
        void 번역이_실패해도_관리자가_직접_입력한_값은_영향받지_않는다() {
            String result = FieldTranslationDecision.decideEnglish(
                    true, "Manual X", "Old Manual", null);

            assertEquals("Manual X", result);
        }
    }

    @Nested
    class 생성_시점_모양_저장된_영문이_없는_경우 {
        // 생성 시에는 storedEnglish가 항상 null이고 koreanChanged는 항상 true다.

        @Test
        void 생성시_영문을_직접_입력하면_그_값이_저장된다() {
            String result = FieldTranslationDecision.decideEnglish(
                    true, "Manual X", null, "Auto Y");

            assertEquals("Manual X", result);
        }

        @Test
        void 생성시_영문을_비워두면_자동번역_결과로_채워진다() {
            String result = FieldTranslationDecision.decideEnglish(
                    true, null, null, "Auto Z");

            assertEquals("Auto Z", result);
        }

        @Test
        void 생성시_영문을_비워뒀는데_번역까지_실패하면_null로_남는다() {
            String result = FieldTranslationDecision.decideEnglish(
                    true, null, null, null);

            assertNull(result);
        }

        @Test
        void 생성시_영문을_직접_입력하면_번역이_실패해도_그_값이_저장된다() {
            String result = FieldTranslationDecision.decideEnglish(
                    true, "Manual X", null, null);

            assertEquals("Manual X", result);
        }
    }

    @Nested
    class 저장된_영문이_없고_한국어도_안바뀐_경우 {
        // update()에서 "이 필드 자체는 안 바뀌었지만 다른 필드 때문에 번역기가 호출된"
        // 상황을 다룬다. storedEnglish가 null이면 koreanChanged와 무관하게 채워진다 —
        // Notice.applyTranslation의 null-허용 가드와 동일한 효과다.

        @Test
        void 저장된_영문이_없으면_이_필드가_안바뀌었어도_자동번역_결과로_채워진다() {
            String result = FieldTranslationDecision.decideEnglish(
                    false, null, null, "Auto From Other Field Trigger");

            assertEquals("Auto From Other Field Trigger", result);
        }

        @Test
        void 저장된_영문도_없고_자동번역도_없으면_null로_남는다() {
            String result = FieldTranslationDecision.decideEnglish(
                    false, null, null, null);

            assertNull(result);
        }

        @Test
        void 저장된_영문이_없어도_직접_입력한_값이_있으면_그_값이_저장된다() {
            String result = FieldTranslationDecision.decideEnglish(
                    false, "Manual X", null, null);

            assertEquals("Manual X", result);
        }
    }
}
