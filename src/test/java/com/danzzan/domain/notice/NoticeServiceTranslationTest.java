package com.danzzan.domain.notice;

import com.danzzan.domain.notice.dto.request.CreateNoticeRequest;
import com.danzzan.domain.notice.dto.request.UpdateNoticeRequest;
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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoticeServiceTranslationTest {

    @Mock
    private NoticeRepository noticeRepository;

    @Mock
    private TranslationService translationService;

    @InjectMocks
    private NoticeService noticeService;

    private CreateNoticeRequest sampleRequest() {
        CreateNoticeRequest request = new CreateNoticeRequest();
        request.setTitle("우천 안내");
        request.setContent("야외 부스 운영이 중단될 수 있습니다.");
        request.setAuthor("총학생회");
        request.setCategory("GENERAL");
        request.setIsPinned(false);
        request.setImages(List.of());
        return request;
    }

    @Test
    void 공지_생성시_영문을_함께_저장한다() {
        when(translationService.translateAll(any()))
                .thenReturn(List.of("Rain notice", "Outdoor booths may close."));
        when(noticeRepository.save(any(Notice.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        noticeService.create(sampleRequest());

        ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
        verify(noticeRepository).save(captor.capture());

        assertEquals("Rain notice", captor.getValue().getTitleEn());
        assertEquals("Outdoor booths may close.", captor.getValue().getContentEn());
    }

    @Test
    void 번역이_실패해도_공지_등록은_성공한다() {
        when(translationService.translateAll(any()))
                .thenReturn(Arrays.asList(null, null));
        when(noticeRepository.save(any(Notice.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        noticeService.create(sampleRequest());

        ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
        verify(noticeRepository).save(captor.capture());

        assertEquals("우천 안내", captor.getValue().getTitle());
        assertNull(captor.getValue().getTitleEn());
    }

    private Notice existingNotice() {
        return Notice.create(
                "우천 안내",
                "야외 부스 운영이 중단될 수 있습니다.",
                "총학생회",
                "GENERAL",
                false,
                null,
                List.of(),
                false
        );
    }

    private UpdateNoticeRequest updateRequest(String title, String content) {
        UpdateNoticeRequest request = new UpdateNoticeRequest();
        request.setTitle(title);
        request.setContent(content);
        request.setAuthor("총학생회");
        request.setCategory("GENERAL");
        request.setIsPinned(false);
        request.setImages(List.of());
        return request;
    }

    @Test
    void 한국어가_바뀌면_재번역한다() {
        Notice notice = existingNotice();
        when(noticeRepository.findById(1L)).thenReturn(Optional.of(notice));
        when(translationService.translateAll(any()))
                .thenReturn(List.of("Sunny notice", "Outdoor booths will resume."));
        when(noticeRepository.save(any(Notice.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        noticeService.update(1L, updateRequest("맑음 안내", "야외 부스 운영이 재개됩니다."));

        verify(translationService).translateAll(any());
        ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
        verify(noticeRepository).save(captor.capture());
        assertEquals("Sunny notice", captor.getValue().getTitleEn());
        assertEquals("Outdoor booths will resume.", captor.getValue().getContentEn());
    }

    @Test
    void 한국어가_바뀌지_않으면_재번역하지_않는다() {
        Notice notice = existingNotice();
        when(noticeRepository.findById(1L)).thenReturn(Optional.of(notice));
        when(noticeRepository.save(any(Notice.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        noticeService.update(1L, updateRequest("우천 안내", "야외 부스 운영이 중단될 수 있습니다."));

        verify(translationService, never()).translateAll(any());
    }

    @Test
    void 한국어가_바뀌지_않아도_수동_영문을_입력하면_저장하고_수동_플래그를_켠다() {
        Notice notice = existingNotice();
        when(noticeRepository.findById(1L)).thenReturn(Optional.of(notice));
        when(noticeRepository.save(any(Notice.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateNoticeRequest request = updateRequest("우천 안내", "야외 부스 운영이 중단될 수 있습니다.");
        request.setTitleEn("Rain notice");
        request.setContentEn("Outdoor booths may close.");

        noticeService.update(1L, request);

        verify(translationService, never()).translateAll(any());
        ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
        verify(noticeRepository).save(captor.capture());
        assertEquals("Rain notice", captor.getValue().getTitleEn());
        assertEquals("Outdoor booths may close.", captor.getValue().getContentEn());
        assertTrue(captor.getValue().isEnIsManual());
    }
}
