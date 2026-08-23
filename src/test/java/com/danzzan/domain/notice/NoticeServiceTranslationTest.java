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

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
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
}
