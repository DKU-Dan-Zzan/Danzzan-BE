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

    @Test
    void 재편집시_비워둔_영문_본문은_낡은_수동값이_아니라_새로_번역한_값으로_채워진다() {
        // enIsManual=true인 기존 공지: 이전 저장에서 관리자가 영문을 직접 입력해 두었다.
        Notice notice = existingNotice();
        notice.applyManualTranslation("Rain notice", "Outdoor booths may close.");
        when(noticeRepository.findById(1L)).thenReturn(Optional.of(notice));
        // 한국어 본문만 바뀌므로 번역기는 (변경 없는 제목, 바뀐 본문)을 번역해 돌려준다.
        when(translationService.translateAll(any()))
                .thenReturn(List.of("Rain notice(auto)", "Outdoor booths will resume soon."));
        when(noticeRepository.save(any(Notice.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // 한국어 제목은 그대로, 한국어 본문만 변경. 영문 제목은 새로 수동 입력, 영문 본문은 비워둠(=자동번역 요청).
        UpdateNoticeRequest request = updateRequest("우천 안내", "야외 부스 운영이 재개됩니다.");
        request.setTitleEn("Rain notice (manual update)");
        request.setContentEn(null);

        noticeService.update(1L, request);

        ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
        verify(noticeRepository).save(captor.capture());
        assertEquals("Rain notice (manual update)", captor.getValue().getTitleEn());
        // 낡은 "Outdoor booths may close."가 아니라 방금 새로 번역된 값이어야 한다.
        assertEquals("Outdoor booths will resume soon.", captor.getValue().getContentEn());
        assertTrue(captor.getValue().isEnIsManual());
    }

    @Test
    void 제목_한국어만_바뀌면_변경없는_본문의_수동_영문은_재번역하지_않는다() {
        // enIsManual=true인 기존 공지.
        Notice notice = existingNotice();
        notice.applyManualTranslation("Rain notice", "Outdoor booths may close.");
        when(noticeRepository.findById(1L)).thenReturn(Optional.of(notice));
        when(translationService.translateAll(any()))
                .thenReturn(List.of("Sunny notice(auto)", "Outdoor booths may close.(auto, unused)"));
        when(noticeRepository.save(any(Notice.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // 한국어 제목만 변경, 본문은 그대로. 영문 제목/본문 모두 비워둠(=자동번역 요청).
        UpdateNoticeRequest request = updateRequest("맑음 안내", "야외 부스 운영이 중단될 수 있습니다.");

        noticeService.update(1L, request);

        ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
        verify(noticeRepository).save(captor.capture());
        assertEquals("Sunny notice(auto)", captor.getValue().getTitleEn());
        // 본문의 한국어가 바뀌지 않았으므로 기존 수동 영문이 그대로 유지되어야 한다.
        assertEquals("Outdoor booths may close.", captor.getValue().getContentEn());
        assertTrue(captor.getValue().isEnIsManual());
    }
}
