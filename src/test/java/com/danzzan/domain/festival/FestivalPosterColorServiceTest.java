package com.danzzan.domain.festival;

import com.danzzan.domain.festival.dto.response.PosterColorResponse;
import com.danzzan.domain.festival.entity.FestivalSetting;
import com.danzzan.domain.festival.repository.FestivalSettingRepository;
import com.danzzan.domain.festival.service.FestivalPosterColorService;
import com.danzzan.domain.festival.service.PosterColorExtractor;
import com.danzzan.infra.s3.S3PosterImageReader;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FestivalPosterColorServiceTest {
    private final FestivalSettingRepository settings = mock(FestivalSettingRepository.class);
    private final S3PosterImageReader reader = mock(S3PosterImageReader.class);
    private final PosterColorExtractor extractor = mock(PosterColorExtractor.class);
    private final FestivalPosterColorService service = new FestivalPosterColorService(settings, reader, extractor);

    @Test void missingPosterAndFestivalReturnClearErrors() {
        when(settings.findById(1L)).thenReturn(Optional.of(setting(1L, null)));
        when(settings.findById(404L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () -> service.extract(1L)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> service.extract(404L)).getStatusCode());
        verifyNoInteractions(reader, extractor);
    }

    @Test void readsOnlyThePosterBelongingToTheRequestedFestival() {
        String first = "https://cdn/festival/1/poster/first.png";
        String second = "https://cdn/festival/2/poster/second.png";
        when(settings.findById(1L)).thenReturn(Optional.of(setting(1L, first)));
        when(settings.findById(2L)).thenReturn(Optional.of(setting(2L, second)));
        byte[] firstBytes = {1}, secondBytes = {2};
        when(reader.read(1L, first)).thenReturn(firstBytes);
        when(reader.read(2L, second)).thenReturn(secondBytes);
        when(extractor.extract(firstBytes)).thenReturn(new PosterColorResponse(List.of(new PosterColorResponse.ColorCandidate("#123456", 0.3))));
        when(extractor.extract(secondBytes)).thenReturn(new PosterColorResponse(List.of(new PosterColorResponse.ColorCandidate("#ABCDEF", 0.4))));
        assertEquals("#123456", service.extract(1L).colors().get(0).hex());
        assertEquals("#ABCDEF", service.extract(2L).colors().get(0).hex());
        verify(reader).read(1L, first);
        verify(reader).read(2L, second);
    }

    @Test void downloadFailureIsPropagated() {
        String poster = "https://cdn/festival/1/poster/image.png";
        when(settings.findById(1L)).thenReturn(Optional.of(setting(1L, poster)));
        when(reader.read(1L, poster)).thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY));
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(ResponseStatusException.class, () -> service.extract(1L)).getStatusCode());
        verifyNoInteractions(extractor);
    }

    private FestivalSetting setting(Long id, String poster) {
        FestivalSetting setting = FestivalSetting.create("School", "Festival", LocalDate.now(), LocalDate.now(), false);
        ReflectionTestUtils.setField(setting, "id", id);
        if (poster != null) setting.updatePosterImageUrl(poster);
        return setting;
    }
}
