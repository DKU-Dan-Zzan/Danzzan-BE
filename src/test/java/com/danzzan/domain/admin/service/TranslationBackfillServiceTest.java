package com.danzzan.domain.admin.service;

import com.danzzan.domain.boothmap.model.entity.Booth;
import com.danzzan.domain.boothmap.model.entity.BoothType;
import com.danzzan.domain.boothmap.repository.BoothRepository;
import com.danzzan.domain.boothmap.repository.CollegeRepository;
import com.danzzan.domain.boothmap.repository.PubRepository;
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
