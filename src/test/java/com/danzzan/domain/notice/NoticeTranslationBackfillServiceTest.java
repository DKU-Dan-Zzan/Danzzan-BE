package com.danzzan.domain.notice;

import com.danzzan.domain.notice.entity.Notice;
import com.danzzan.domain.notice.repository.NoticeRepository;
import com.danzzan.domain.notice.service.NoticeTranslationBackfillService;
import com.danzzan.infra.translation.TranslationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoticeTranslationBackfillServiceTest {

    @Mock
    private NoticeRepository noticeRepository;

    @Mock
    private TranslationService translationService;

    @InjectMocks
    private NoticeTranslationBackfillService backfillService;

    private Notice untranslatedNotice() {
        return Notice.create("제목", "내용", "작성자", "GENERAL",
                false, null, List.of(), false);
    }

    @Test
    void 영문이_비어있는_공지를_채운다() {
        when(noticeRepository.findTop50ByTitleEnIsNullOrContentEnIsNull())
                .thenReturn(List.of(untranslatedNotice()));
        when(translationService.translateAll(any()))
                .thenReturn(List.of("Title", "Content"));

        assertEquals(1, backfillService.backfill());
    }

    @Test
    void 채울_공지가_없으면_번역을_호출하지_않는다() {
        when(noticeRepository.findTop50ByTitleEnIsNullOrContentEnIsNull())
                .thenReturn(List.of());

        assertEquals(0, backfillService.backfill());
        verify(translationService, never()).translateAll(any());
    }

    @Test
    void 번역이_또_실패하면_영문을_비운_채로_남긴다() {
        Notice notice = untranslatedNotice();
        when(noticeRepository.findTop50ByTitleEnIsNullOrContentEnIsNull())
                .thenReturn(List.of(notice));
        when(translationService.translateAll(any()))
                .thenReturn(Arrays.asList(null, null));

        backfillService.backfill();

        assertNull(notice.getTitleEn());
    }

    @Test
    void 이전에_채워진_필드가_있어도_이번_호출에서_새로_채운_것이_없으면_0이다() {
        Notice notice = untranslatedNotice();
        notice.applyTranslation("Title EN", null);
        when(noticeRepository.findTop50ByTitleEnIsNullOrContentEnIsNull())
                .thenReturn(List.of(notice));
        when(translationService.translateAll(any()))
                .thenReturn(Arrays.asList(null, null));

        assertEquals(0, backfillService.backfill());
    }

    @Test
    void 이미_채워진_필드는_빈_문자열로_번역을_요청하고_빈_필드만_한국어_원문을_보낸다() {
        Notice notice = untranslatedNotice();
        notice.applyTranslation("Title EN", null);
        when(noticeRepository.findTop50ByTitleEnIsNullOrContentEnIsNull())
                .thenReturn(List.of(notice));
        when(translationService.translateAll(any()))
                .thenReturn(List.of("Title EN", "Content EN"));

        backfillService.backfill();

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(translationService).translateAll(captor.capture());
        assertEquals(List.of("", "내용"), captor.getValue());
    }
}
