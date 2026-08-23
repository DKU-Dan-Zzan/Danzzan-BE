package com.danzzan.domain.timetable;

import com.danzzan.domain.timetable.dto.admin.request.CreateArtistRequest;
import com.danzzan.domain.timetable.dto.admin.request.CreatePerformanceRequest;
import com.danzzan.domain.timetable.dto.admin.request.UpdateArtistRequest;
import com.danzzan.domain.timetable.dto.admin.request.UpdatePerformanceRequest;
import com.danzzan.domain.timetable.model.entity.Artist;
import com.danzzan.domain.timetable.model.entity.Performance;
import com.danzzan.domain.timetable.repository.ArtistRepository;
import com.danzzan.domain.timetable.repository.PerformanceRepository;
import com.danzzan.domain.timetable.service.AdminArtistService;
import com.danzzan.domain.timetable.service.AdminPerformanceService;
import com.danzzan.infra.s3.S3PresignService;
import com.danzzan.infra.translation.TranslationService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 아티스트/공연 도메인 서비스의 번역 연결을 검증한다.
 * NoticeServiceTranslationTest와 같은 형태: 생성 시 항상 번역하고,
 * 수정 시에는 한국어가 실제로 바뀐 경우에만 재번역한다.
 */
@ExtendWith(MockitoExtension.class)
class TimetableTranslationTest {

    @Nested
    class 아티스트_번역 {

        @Mock
        private ArtistRepository artistRepository;
        @Mock
        private PerformanceRepository performanceRepository;
        @Mock
        private S3PresignService s3PresignService;
        @Mock
        private TranslationService translationService;

        @InjectMocks
        private AdminArtistService adminArtistService;

        @Test
        void 아티스트_생성시_영문을_함께_저장한다() {
            CreateArtistRequest request = new CreateArtistRequest();
            request.setName("잔나비");
            request.setDescription("감성 록밴드");

            when(translationService.translateAll(any()))
                    .thenReturn(List.of("Jannabi", "Emotional rock band"));
            when(artistRepository.save(any(Artist.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            adminArtistService.createArtist(request);

            ArgumentCaptor<Artist> captor = ArgumentCaptor.forClass(Artist.class);
            verify(artistRepository).save(captor.capture());

            assertEquals("Jannabi", captor.getValue().getNameEn());
            assertEquals("Emotional rock band", captor.getValue().getDescriptionEn());
        }

        private Artist existingArtist() {
            return Artist.create("잔나비", "감성 록밴드", null);
        }

        @Test
        void 아티스트_한국어가_바뀌면_재번역한다() {
            Artist artist = existingArtist();
            when(artistRepository.findById(1)).thenReturn(Optional.of(artist));
            when(translationService.translateAll(any()))
                    .thenReturn(List.of("New Name", "New Description"));

            UpdateArtistRequest request = new UpdateArtistRequest();
            request.setName("새 이름");
            request.setDescription("새 소개");

            adminArtistService.updateArtist(1, request);

            verify(translationService).translateAll(any());
            assertEquals("New Name", artist.getNameEn());
            assertEquals("New Description", artist.getDescriptionEn());
        }

        @Test
        void 아티스트_한국어가_바뀌지_않으면_재번역하지_않는다() {
            Artist artist = existingArtist();
            when(artistRepository.findById(1)).thenReturn(Optional.of(artist));

            UpdateArtistRequest request = new UpdateArtistRequest();
            request.setName("잔나비");
            request.setDescription("감성 록밴드");

            adminArtistService.updateArtist(1, request);

            verify(translationService, never()).translateAll(any());
        }
    }

    @Nested
    class 공연_번역 {

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

        @Test
        void 공연_생성시_영문을_함께_저장한다() {
            when(artistRepository.findById(1)).thenReturn(Optional.of(artistWithId(1)));
            when(translationService.translate(any())).thenReturn("Main Stage");
            when(performanceRepository.save(any(Performance.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            CreatePerformanceRequest request = new CreatePerformanceRequest();
            request.setArtistId(1);
            request.setPerformanceDate(LocalDate.of(2026, 5, 13));
            request.setStartTime(LocalTime.of(18, 0));
            request.setEndTime(LocalTime.of(18, 30));
            request.setStage("메인 스테이지");

            adminPerformanceService.createPerformance(request);

            ArgumentCaptor<Performance> captor = ArgumentCaptor.forClass(Performance.class);
            verify(performanceRepository).save(captor.capture());

            assertEquals("Main Stage", captor.getValue().getStageEn());
        }

        private Performance existingPerformance() {
            return Performance.create(
                    LocalDate.of(2026, 5, 13),
                    LocalTime.of(18, 0),
                    LocalTime.of(18, 30),
                    artistWithId(1),
                    "메인 스테이지"
            );
        }

        @Test
        void 공연_스테이지가_바뀌면_재번역한다() {
            Performance performance = existingPerformance();
            when(performanceRepository.findByIdWithArtist(1)).thenReturn(Optional.of(performance));
            when(translationService.translate("새 스테이지")).thenReturn("New Stage");

            UpdatePerformanceRequest request = new UpdatePerformanceRequest();
            request.setStage("새 스테이지");

            adminPerformanceService.updatePerformance(1, request);

            verify(translationService).translate("새 스테이지");
            assertEquals("New Stage", performance.getStageEn());
        }

        @Test
        void 공연_스테이지가_바뀌지_않으면_재번역하지_않는다() {
            Performance performance = existingPerformance();
            when(performanceRepository.findByIdWithArtist(1)).thenReturn(Optional.of(performance));

            UpdatePerformanceRequest request = new UpdatePerformanceRequest();
            request.setStage("메인 스테이지");

            adminPerformanceService.updatePerformance(1, request);

            verify(translationService, never()).translate(any());
        }
    }
}
