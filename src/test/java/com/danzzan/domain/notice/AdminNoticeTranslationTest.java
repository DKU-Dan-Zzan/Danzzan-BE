package com.danzzan.domain.notice;

import com.danzzan.domain.notice.dto.request.CreateNoticeRequest;
import com.danzzan.domain.notice.entity.Notice;
import com.danzzan.domain.notice.repository.NoticeRepository;
import com.danzzan.domain.notice.service.NoticeService;
import com.danzzan.infra.translation.TranslationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminNoticeTranslationTest {

    @Mock
    private NoticeRepository noticeRepository;

    @Mock
    private TranslationService translationService;

    @InjectMocks
    private NoticeService noticeService;

    private CreateNoticeRequest requestWithEnglish(String titleEn, String contentEn) {
        CreateNoticeRequest request = new CreateNoticeRequest();
        request.setTitle("우천 안내");
        request.setContent("야외 부스가 중단될 수 있습니다.");
        request.setAuthor("총학생회");
        request.setCategory("GENERAL");
        request.setIsPinned(false);
        request.setImages(List.of());
        request.setTitleEn(titleEn);
        request.setContentEn(contentEn);
        return request;
    }

    @Test
    void 관리자가_영문을_입력하면_자동번역을_호출하지_않는다() {
        when(noticeRepository.save(any(Notice.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        noticeService.create(requestWithEnglish("Rain notice", "Outdoor booths may close."));

        verify(translationService, never()).translateAll(any());

        ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
        verify(noticeRepository).save(captor.capture());
        assertEquals("Rain notice", captor.getValue().getTitleEn());
        assertTrue(captor.getValue().isEnIsManual());
    }

    @Test
    void 영문을_비워두면_자동번역을_호출한다() {
        when(translationService.translateAll(any()))
                .thenReturn(List.of("Auto title", "Auto content"));
        when(noticeRepository.save(any(Notice.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        noticeService.create(requestWithEnglish(null, null));

        verify(translationService).translateAll(any());

        ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
        verify(noticeRepository).save(captor.capture());
        assertEquals("Auto title", captor.getValue().getTitleEn());
        assertEquals(false, captor.getValue().isEnIsManual());
    }
}
