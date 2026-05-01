package com.danzzan.domain.advertisement;

import com.danzzan.domain.advertisement.dto.CreateAdvertisementRequest;
import com.danzzan.infra.s3.S3PresignService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAdvertisementServiceTest {

    @Mock
    private AdvertisementRepository advertisementRepository;

    @Mock
    private S3PresignService s3PresignService;

    @InjectMocks
    private AdminAdvertisementService adminAdvertisementService;

    @Test
    void 광고_생성시_https_이동_URL을_저장한다() {
        when(advertisementRepository.save(any(Advertisement.class)))
                .thenAnswer(invocation -> {
                    Advertisement ad = invocation.getArgument(0);
                    ad.setId(1L);
                    return ad;
                });

        CreateAdvertisementRequest request = baseRequest();
        request.setLinkUrl("https://example.com/landing");

        adminAdvertisementService.createOrReplace(request);

        ArgumentCaptor<Advertisement> captor = ArgumentCaptor.forClass(Advertisement.class);
        verify(advertisementRepository).save(captor.capture());
        assertEquals("https://example.com/landing", captor.getValue().getLinkUrl());
    }

    @Test
    void 광고_생성시_이동_URL이_없으면_null로_저장한다() {
        when(advertisementRepository.save(any(Advertisement.class)))
                .thenAnswer(invocation -> {
                    Advertisement ad = invocation.getArgument(0);
                    ad.setId(1L);
                    return ad;
                });

        CreateAdvertisementRequest request = baseRequest();
        request.setLinkUrl("   ");

        adminAdvertisementService.createOrReplace(request);

        ArgumentCaptor<Advertisement> captor = ArgumentCaptor.forClass(Advertisement.class);
        verify(advertisementRepository).save(captor.capture());
        assertNull(captor.getValue().getLinkUrl());
    }

    @Test
    void 광고_생성시_http_URL은_예외가_발생한다() {
        CreateAdvertisementRequest request = baseRequest();
        request.setLinkUrl("http://example.com");

        assertThrows(ResponseStatusException.class, () -> adminAdvertisementService.createOrReplace(request));
        verify(advertisementRepository, never()).save(any(Advertisement.class));
    }

    @Test
    void 광고_수정시_https_이동_URL을_저장한다() {
        Advertisement existing = new Advertisement();
        existing.setId(11L);
        existing.setTitle("기존 광고");
        existing.setImageUrl("https://cdn.example.com/old.png");
        existing.setPlacement(AdvertisementPlacement.HOME_BOTTOM);
        existing.setIsActive(true);

        when(advertisementRepository.findById(11L)).thenReturn(Optional.of(existing));
        when(advertisementRepository.save(any(Advertisement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateAdvertisementRequest request = baseRequest();
        request.setTitle("수정 광고");
        request.setLinkUrl("https://example.com/new-landing");

        adminAdvertisementService.updateById(11L, request);

        ArgumentCaptor<Advertisement> captor = ArgumentCaptor.forClass(Advertisement.class);
        verify(advertisementRepository).save(captor.capture());
        assertEquals("수정 광고", captor.getValue().getTitle());
        assertEquals("https://example.com/new-landing", captor.getValue().getLinkUrl());
    }

    @Test
    void 광고_수정시_공백_URL은_null로_저장한다() {
        Advertisement existing = new Advertisement();
        existing.setId(12L);
        existing.setTitle("기존 광고");
        existing.setImageUrl("https://cdn.example.com/old.png");
        existing.setPlacement(AdvertisementPlacement.HOME_BOTTOM);
        existing.setIsActive(true);

        when(advertisementRepository.findById(12L)).thenReturn(Optional.of(existing));
        when(advertisementRepository.save(any(Advertisement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateAdvertisementRequest request = baseRequest();
        request.setLinkUrl("   ");

        adminAdvertisementService.updateById(12L, request);

        ArgumentCaptor<Advertisement> captor = ArgumentCaptor.forClass(Advertisement.class);
        verify(advertisementRepository).save(captor.capture());
        assertNull(captor.getValue().getLinkUrl());
    }

    @Test
    void 광고_수정시_http_URL은_예외가_발생한다() {
        Advertisement existing = new Advertisement();
        existing.setId(13L);
        existing.setTitle("기존 광고");
        existing.setImageUrl("https://cdn.example.com/old.png");
        existing.setPlacement(AdvertisementPlacement.HOME_BOTTOM);
        existing.setIsActive(true);

        when(advertisementRepository.findById(13L)).thenReturn(Optional.of(existing));

        CreateAdvertisementRequest request = baseRequest();
        request.setLinkUrl("http://example.com/not-allowed");

        assertThrows(ResponseStatusException.class, () -> adminAdvertisementService.updateById(13L, request));
        verify(advertisementRepository, never()).save(any(Advertisement.class));
    }

    private CreateAdvertisementRequest baseRequest() {
        CreateAdvertisementRequest request = new CreateAdvertisementRequest();
        request.setTitle("테스트 광고");
        request.setImageUrl("https://cdn.example.com/banner.png");
        request.setPlacement(AdvertisementPlacement.HOME_BOTTOM);
        return request;
    }
}
