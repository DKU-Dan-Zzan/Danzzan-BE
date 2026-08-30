package com.danzzan.domain.admin.service;

import com.danzzan.domain.boothmap.model.entity.Booth;
import com.danzzan.domain.boothmap.model.entity.BoothType;
import com.danzzan.domain.boothmap.repository.BoothRepository;
import com.danzzan.domain.boothmap.repository.CollegeRepository;
import com.danzzan.domain.boothmap.repository.PubRepository;
import com.danzzan.domain.notice.repository.EmergencyNoticeRepository;
import com.danzzan.domain.notice.service.NoticeTranslationBackfillService;
import com.danzzan.domain.timetable.repository.ArtistRepository;
import com.danzzan.domain.timetable.repository.PerformanceRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TranslationBackfillServiceTest {

    @Mock
    private NoticeTranslationBackfillService noticeBackfillService;

    @Mock
    private BoothRepository boothRepository;

    @Mock
    private PubRepository pubRepository;

    @Mock
    private CollegeRepository collegeRepository;

    @Mock
    private ArtistRepository artistRepository;

    @Mock
    private PerformanceRepository performanceRepository;

    @Mock
    private TranslationService translationService;

    @Mock
    private EmergencyNoticeRepository emergencyNoticeRepository;

    @Mock
    private TranslationBackfillWriter writer;

    @InjectMocks
    private TranslationBackfillService backfillService;

    private Booth untranslatedBooth() {
        return new Booth("체험 부스", BoothType.EXPERIENCE, "부스 설명", null, null, null);
    }

    @Test
    void 부스는_이미_채워진_이름은_빈_문자열로_번역을_요청하고_빈_설명만_한국어_원문을_보낸다() {
        Booth booth = untranslatedBooth();
        booth.applyTranslation("Experience Booth", null);
        when(boothRepository.findNeedingTranslation(any()))
                .thenReturn(List.of(booth));
        when(translationService.translateAll(any()))
                .thenReturn(List.of("Experience Booth", "Booth Description"));

        backfillService.backfillAll();

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(translationService).translateAll(captor.capture());
        assertEquals(List.of("", "부스 설명"), captor.getValue());
    }

    /**
     * 이 테스트가 이 클래스의 존재 이유다.
     *
     * <p>예전에는 backfillAll() 전체가 하나의 트랜잭션이라, 한 행이 저장에
     * 실패하면 같은 회차에 번역한 행이 전부 함께 롤백됐다. 5분 뒤 같은 행을
     * 다시 번역하니 DeepL 요금만 나가고 저장은 영원히 되지 않았다. 실제로
     * booth.description_en 의 길이 초과로 이 일이 136회 반복되며 무료 쿼터
     * 100만 자가 소진됐다.</p>
     */
    @Test
    void 한_행의_저장이_실패해도_나머지_행은_계속_저장한다() {
        when(boothRepository.findNeedingTranslation(any()))
                .thenReturn(List.of(untranslatedBooth(), untranslatedBooth()));
        when(translationService.translateAll(any()))
                .thenReturn(List.of("Experience Booth", "Booth Description"));
        when(writer.applyBooth(any(), any(), any()))
                .thenThrow(new RuntimeException("Data too long for column 'description_en'"))
                .thenReturn(1);

        assertEquals(1, backfillService.backfillAll());

        // 첫 행이 실패해도 둘째 행의 번역까지 진행돼야 한다.
        verify(translationService, times(2)).translateAll(any());
    }

    @Test
    void 번역이_모두_실패하면_backfillAll은_0을_반환한다() {
        Booth booth = untranslatedBooth();
        when(boothRepository.findNeedingTranslation(any()))
                .thenReturn(List.of(booth));
        when(translationService.translateAll(any()))
                .thenReturn(Arrays.asList(null, null));

        assertEquals(0, backfillService.backfillAll());
    }
}
