package com.danzzan.domain.timetable;

import com.danzzan.domain.timetable.dto.admin.request.CreateArtistRequest;
import com.danzzan.domain.timetable.dto.admin.request.UpdateArtistRequest;
import com.danzzan.domain.timetable.dto.admin.response.AdminArtistResponse;
import com.danzzan.domain.timetable.model.entity.Artist;
import com.danzzan.domain.timetable.repository.ArtistRepository;
import com.danzzan.domain.timetable.repository.PerformanceRepository;
import com.danzzan.domain.timetable.service.AdminArtistService;
import com.danzzan.infra.s3.S3PresignService;
import com.danzzan.infra.translation.TranslationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminArtistService의 관리자 수동 영문 입력(decideEnglish) 연결을 검증한다.
 * AdminBoothManagementServiceTranslationTest와 같은 형태를 따른다.
 */
@ExtendWith(MockitoExtension.class)
class AdminArtistServiceTranslationTest {

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

    private CreateArtistRequest createRequest() {
        CreateArtistRequest request = new CreateArtistRequest();
        request.setName("잔나비");
        request.setDescription("감성 록밴드");
        return request;
    }

    @Test
    void 아티스트_생성시_영문을_직접_입력하면_그대로_저장되고_수동_플래그가_켜진다() {
        when(translationService.translateAll(any()))
                .thenReturn(List.of("Auto Name", "Auto Desc"));
        when(artistRepository.save(any(Artist.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateArtistRequest request = createRequest();
        request.setNameEn("Manual Name");
        request.setDescriptionEn("Manual Desc");

        AdminArtistResponse response = adminArtistService.createArtist(request);

        ArgumentCaptor<Artist> captor = ArgumentCaptor.forClass(Artist.class);
        verify(artistRepository).save(captor.capture());

        Artist saved = captor.getValue();
        assertEquals("Manual Name", saved.getNameEn());
        assertEquals("Manual Desc", saved.getDescriptionEn());
        assertEquals(true, saved.isEnIsManual());
        assertEquals(true, response.isEnIsManual());
    }

    @Test
    void 아티스트_생성시_영문을_비워두면_자동번역이_저장되고_수동_플래그는_꺼진다() {
        when(translationService.translateAll(any()))
                .thenReturn(List.of("Auto Name", "Auto Desc"));
        when(artistRepository.save(any(Artist.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdminArtistResponse response = adminArtistService.createArtist(createRequest());

        ArgumentCaptor<Artist> captor = ArgumentCaptor.forClass(Artist.class);
        verify(artistRepository).save(captor.capture());

        Artist saved = captor.getValue();
        assertEquals("Auto Name", saved.getNameEn());
        assertEquals("Auto Desc", saved.getDescriptionEn());
        assertEquals(false, saved.isEnIsManual());
        assertEquals(false, response.isEnIsManual());
    }

    private Artist existingAutoTranslatedArtist() {
        Artist artist = Artist.create("잔나비", "감성 록밴드", null);
        artist.applyTranslation("Jannabi", "Emotional rock band");
        ReflectionTestUtils.setField(artist, "id", 1);
        return artist;
    }

    @Test
    void 아티스트_수정시_한국어_설명을_비우면_영문_설명도_비워진다() {
        Artist artist = existingAutoTranslatedArtist();
        when(artistRepository.findById(1)).thenReturn(Optional.of(artist));
        // 설명 슬롯만 비어 있으므로 그 슬롯만 null로 돌아온다(설계된 동작).
        when(translationService.translateAll(any()))
                .thenReturn(Arrays.asList("Jannabi", null));

        UpdateArtistRequest request = new UpdateArtistRequest();
        request.setDescription("");

        adminArtistService.updateArtist(1, request);

        assertNull(artist.getDescriptionEn());
        assertEquals("Jannabi", artist.getNameEn());
        assertEquals(false, artist.isEnIsManual());
    }

    @Test
    void 아티스트_수정시_한국어도_영문도_안바뀌면_재번역_호출이_없다() {
        Artist artist = existingAutoTranslatedArtist();
        when(artistRepository.findById(1)).thenReturn(Optional.of(artist));

        UpdateArtistRequest request = new UpdateArtistRequest();
        request.setName("잔나비");
        request.setDescription("감성 록밴드");

        adminArtistService.updateArtist(1, request);

        verify(translationService, never()).translateAll(any());
        assertEquals("Jannabi", artist.getNameEn());
        assertEquals("Emotional rock band", artist.getDescriptionEn());
    }
}
