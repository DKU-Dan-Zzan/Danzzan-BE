package com.danzzan.domain.notice;

import com.danzzan.domain.notice.dto.response.NoticeResponse;
import com.danzzan.domain.notice.entity.Notice;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NoticeResponseLangTest {

    private Notice noticeWithEnglish() {
        Notice notice = Notice.create("제목", "내용", "작성자", "GENERAL",
                false, null, List.of(), false);
        notice.applyTranslation("Title", "Content");
        return notice;
    }

    @Test
    void 영어_요청시_영문_필드값을_같은_필드명으로_반환한다() {
        NoticeResponse response = NoticeResponse.from(noticeWithEnglish(), true);

        assertEquals("Title", response.getTitle());
        assertEquals("Content", response.getContent());
    }

    @Test
    void 한국어_요청시_한국어를_반환한다() {
        NoticeResponse response = NoticeResponse.from(noticeWithEnglish(), false);

        assertEquals("제목", response.getTitle());
    }

    @Test
    void 영문이_없으면_한국어로_폴백한다() {
        Notice notice = Notice.create("제목", "내용", "작성자", "GENERAL",
                false, null, List.of(), false);

        NoticeResponse response = NoticeResponse.from(notice, true);

        assertEquals("제목", response.getTitle());
        assertEquals("내용", response.getContent());
    }
}
