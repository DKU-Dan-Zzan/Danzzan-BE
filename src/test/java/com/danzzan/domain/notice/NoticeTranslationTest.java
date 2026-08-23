package com.danzzan.domain.notice;

import com.danzzan.domain.notice.entity.Notice;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoticeTranslationTest {

    private Notice sampleNotice() {
        return Notice.create("제목", "내용", "작성자", "GENERAL",
                false, null, List.of(), false);
    }

    @Test
    void 새_공지는_영문이_비어있고_수동플래그가_꺼져있다() {
        Notice notice = sampleNotice();

        assertNull(notice.getTitleEn());
        assertNull(notice.getContentEn());
        assertFalse(notice.isEnIsManual());
    }

    @Test
    void 자동번역_적용시_수동플래그는_켜지지_않는다() {
        Notice notice = sampleNotice();

        notice.applyTranslation("Title", "Content");

        assertEquals("Title", notice.getTitleEn());
        assertEquals("Content", notice.getContentEn());
        assertFalse(notice.isEnIsManual());
    }

    @Test
    void 수동번역_적용시_수동플래그가_켜진다() {
        Notice notice = sampleNotice();

        notice.applyManualTranslation("Manual Title", "Manual Content");

        assertEquals("Manual Title", notice.getTitleEn());
        assertTrue(notice.isEnIsManual());
    }

    @Test
    void 자동번역은_수동플래그가_켜진_공지를_덮어쓰지_않는다() {
        Notice notice = sampleNotice();
        notice.applyManualTranslation("Manual Title", "Manual Content");

        notice.applyTranslation("Machine Title", "Machine Content");

        assertEquals("Manual Title", notice.getTitleEn());
        assertEquals("Manual Content", notice.getContentEn());
    }

    @Test
    void 자동번역의_null_값은_기존_영문을_지우지_않는다() {
        Notice notice = sampleNotice();
        notice.applyTranslation("Title", "Content");

        notice.applyTranslation(null, null);

        assertEquals("Title", notice.getTitleEn());
        assertEquals("Content", notice.getContentEn());
    }
}
