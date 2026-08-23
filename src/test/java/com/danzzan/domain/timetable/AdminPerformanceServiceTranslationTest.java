package com.danzzan.domain.timetable;

import com.danzzan.domain.timetable.dto.admin.request.CreatePerformanceRequest;
import com.danzzan.domain.timetable.dto.admin.request.UpdatePerformanceRequest;
import com.danzzan.domain.timetable.dto.admin.response.AdminPerformanceResponse;
import com.danzzan.domain.timetable.model.entity.Artist;
import com.danzzan.domain.timetable.model.entity.Performance;
import com.danzzan.domain.timetable.repository.ArtistRepository;
import com.danzzan.domain.timetable.repository.PerformanceRepository;
import com.danzzan.domain.timetable.service.AdminPerformanceService;
import com.danzzan.infra.translation.TranslationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminPerformanceService의 관리자 수동 영문 입력(decideEnglish) 연결을 검증한다.
 * AdminBoothManagementServiceTranslationTest와 같은 형태를 따른다.
 */
@ExtendWith(MockitoExtension.class)
class AdminPerformanceServiceTranslationTest {

    @Mock
    private PerformanceRepository performanceRepository;
    @Mock
    private ArtistRepository artistRepository;
    @Mock
    private TranslationService translationService;

    @InjectMocks
    private AdminPerformanceService adminPerformanceService;

    private Artist artistWithId(int id) {
        Artist artist = Artist.create("잔나비", "감성 록밴드", null);
        ReflectionTestUtils.setField(artist, "id", id);
        return artist;
    }

    private CreatePerformanceRequest createRequest() {
        CreatePerformanceRequest request = new CreatePerformanceRequest();
        request.setArtistId(1);
        request.setPerformanceDate(LocalDate.of(2026, 5, 13));
        request.setStartTime(LocalTime.of(18, 0));
        request.setEndTime(LocalTime.of(18, 30));
        request.setStage("메인 스테이지");
        return request;
    }

    @Test
    void 공연_생성시_영문을_직접_입력하면_그대로_저장되고_수동_플래그가_켜진다() {
        when(artistRepository.findById(1)).thenReturn(Optional.of(artistWithId(1)));
        when(translationService.translate(any())).thenReturn("Auto Stage");
        when(performanceRepository.save(any(Performance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreatePerformanceRequest request = createRequest();
        request.setStageEn("Manual Stage");

        AdminPerformanceResponse response = adminPerformanceService.createPerformance(request);

        ArgumentCaptor<Performance> captor = ArgumentCaptor.forClass(Performance.class);
        verify(performanceRepository).save(captor.capture());

        Performance saved = captor.getValue();
        assertEquals("Manual Stage", saved.getStageEn());
        assertEquals(true, saved.isEnIsManual());
        assertEquals(true, response.isEnIsManual());
    }

    @Test
    void 공연_생성시_영문을_비워두면_자동번역이_저장되고_수동_플래그는_꺼진다() {
        when(artistRepository.findById(1)).thenReturn(Optional.of(artistWithId(1)));
        when(translationService.translate(any())).thenReturn("Auto Stage");
        when(performanceRepository.save(any(Performance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdminPerformanceResponse response = adminPerformanceService.createPerformance(createRequest());

        ArgumentCaptor<Performance> captor = ArgumentCaptor.forClass(Performance.class);
        verify(performanceRepository).save(captor.capture());

        Performance saved = captor.getValue();
        assertEquals("Auto Stage", saved.getStageEn());
        assertEquals(false, saved.isEnIsManual());
        assertEquals(false, response.isEnIsManual());
    }

    private Performance existingAutoTranslatedPerformance() {
        Performance performance = Performance.create(
                LocalDate.of(2026, 5, 13),
                LocalTime.of(18, 0),
                LocalTime.of(18, 30),
                artistWithId(1),
                "메인 스테이지"
        );
        performance.applyTranslation("Main Stage");
        ReflectionTestUtils.setField(performance, "id", 1);
        return performance;
    }

    @Test
    void 공연_수정시_한국어_스테이지를_비우면_영문_스테이지도_비워진다() {
        Performance performance = existingAutoTranslatedPerformance();
        when(performanceRepository.findByIdWithArtist(1)).thenReturn(Optional.of(performance));
        // 스테이지 한국어가 비어 있으므로 TranslationService는 null을 돌려준다(설계된 동작).
        when(translationService.translate(null)).thenReturn(null);

        UpdatePerformanceRequest request = new UpdatePerformanceRequest();
        request.setStage("");

        adminPerformanceService.updatePerformance(1, request);

        assertNull(performance.getStageEn());
        assertEquals(false, performance.isEnIsManual());
    }

    @Test
    void 공연_수정시_한국어도_영문도_안바뀌면_재번역_호출이_없다() {
        Performance performance = existingAutoTranslatedPerformance();
        when(performanceRepository.findByIdWithArtist(1)).thenReturn(Optional.of(performance));

        UpdatePerformanceRequest request = new UpdatePerformanceRequest();
        request.setStage("메인 스테이지");

        adminPerformanceService.updatePerformance(1, request);

        verify(translationService, never()).translate(any());
        assertEquals("Main Stage", performance.getStageEn());
    }
}
