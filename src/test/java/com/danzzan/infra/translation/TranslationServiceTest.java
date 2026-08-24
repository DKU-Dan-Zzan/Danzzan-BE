package com.danzzan.infra.translation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TranslationServiceTest {

    @Mock
    private TranslationClient translationClient;

    @Mock
    private GlossarySyncService glossarySyncService;

    @InjectMocks
    private TranslationService translationService;

    @Test
    void 번역에_성공하면_영문을_반환한다() {
        when(glossarySyncService.getGlossaryId()).thenReturn("g-1");
        when(translationClient.translate(List.of("안녕"), "g-1"))
                .thenReturn(List.of("Hello"));

        assertEquals("Hello", translationService.translate("안녕"));
    }

    @Test
    void 번역이_실패하면_null을_반환하고_예외를_던지지_않는다() {
        when(glossarySyncService.getGlossaryId()).thenReturn("g-1");
        when(translationClient.translate(any(), any()))
                .thenThrow(new TranslationUnavailableException("DeepL 장애"));

        assertNull(translationService.translate("안녕"));
    }

    @Test
    void 빈_문자열은_호출없이_null을_반환한다() {
        assertNull(translationService.translate(null));
        assertNull(translationService.translate(""));
        assertNull(translationService.translate("   "));

        verify(translationClient, never()).translate(any(), any());
    }

    @Test
    void 목록에서_빈_항목은_건너뛰고_나머지만_번역한다() {
        when(glossarySyncService.getGlossaryId()).thenReturn(null);
        when(translationClient.translate(List.of("첫째", "셋째"), null))
                .thenReturn(List.of("First", "Third"));

        List<String> result = translationService.translateAll(
                Arrays.asList("첫째", "", "셋째"));

        assertEquals(Arrays.asList("First", null, "Third"), result);
    }

    @Test
    void 목록_번역이_실패하면_전부_null인_같은_크기_목록을_반환한다() {
        when(glossarySyncService.getGlossaryId()).thenReturn(null);
        when(translationClient.translate(any(), any()))
                .thenThrow(new TranslationUnavailableException("DeepL 장애"));

        List<String> result = translationService.translateAll(List.of("가", "나", "다"));

        assertEquals(3, result.size());
        assertEquals(Arrays.asList(null, null, null), result);
    }

    @Test
    void 전부_빈_목록이면_호출하지_않는다() {
        List<String> result = translationService.translateAll(Arrays.asList("", null, "  "));

        assertEquals(Arrays.asList(null, null, null), result);
        verify(translationClient, never()).translate(any(), any());
    }

    @Test
    void 용어집_ID가_null이어도_번역을_수행한다() {
        when(glossarySyncService.getGlossaryId()).thenReturn(null);
        when(translationClient.translate(List.of("부스"), null))
                .thenReturn(List.of("Booth"));

        assertEquals("Booth", translationService.translate("부스"));
        verify(translationClient).translate(eq(List.of("부스")), isNull());
    }
}
