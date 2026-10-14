package com.danzzan.infra.image;

import com.danzzan.domain.festival.dto.request.UpdateFestivalTicketingSettingsRequest;
import com.danzzan.domain.festival.dto.response.FestivalBrandingResponse;
import com.danzzan.domain.festival.background.PosterBackgroundService;
import com.danzzan.domain.festival.theme.dto.ThemeBundleRequest;
import com.danzzan.domain.home.model.dto.HomeBannerPreviewRequest;
import com.danzzan.domain.home.service.HomeBannerPreviewService;
import com.danzzan.domain.notice.dto.request.CreateNoticeRequest;
import com.danzzan.domain.notice.dto.response.NoticeResponse;
import com.danzzan.domain.boothmap.util.ThumbnailUrlResolver;
import com.danzzan.infra.azure.AzureBlobStorage;
import com.danzzan.infra.s3.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImageJsonBoundaryTest {
    private static final String REF = "azure:images/notices/images/test.png";
    private static final String URL = "https://demo.blob.core.windows.net/images/notices/images/test.png?sig=temporary&se=2026-10-12";
    private final AzureBlobStorage storage = mock(AzureBlobStorage.class);

    private ApplicationContextRunner runner() {
        when(storage.normalizeReference(URL)).thenReturn(REF);
        when(storage.readUrl(REF)).thenReturn(URL);
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .withBean(AzureBlobStorage.class, () -> storage);
    }

    @Test void responseSignsRecordAndListFieldsWithoutChangingDomainValues() {
        runner().run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            var branding = new FestivalBrandingResponse(1L, REF, null);
            assertEquals(URL, mapper.readTree(mapper.writeValueAsString(branding)).get("posterImageUrl").asText());
            assertEquals(REF, branding.posterImageUrl());
            var notice = new NoticeResponse();
            ReflectionTestUtils.setField(notice, "imageUrls", List.of(REF));
            ReflectionTestUtils.setField(notice, "thumbnailImageUrl", REF);
            var json = mapper.readTree(mapper.writeValueAsString(notice));
            assertEquals(URL, json.get("imageUrls").get(0).asText());
            assertEquals(URL, json.get("thumbnailImageUrl").asText());
            assertEquals(List.of(REF), notice.getImageUrls());
            assertEquals(URL, mapper.readTree(mapper.writeValueAsString(new ImageUploadResponse(REF, "key"))).get("imageUrl").asText());
            var recommendation = new PosterBackgroundService.Recommendation(
                    "ticket", "image/jpeg", "preview", 1, 1, .5, .5, "reason", REF);
            assertEquals(URL, mapper.readTree(mapper.writeValueAsString(recommendation)).get("sourcePosterUrl").asText());
            var preview = new HomeBannerPreviewService.Preview("image/jpeg", "preview", 1, 1, REF);
            assertEquals(URL, mapper.readTree(mapper.writeValueAsString(preview)).get("sourcePosterUrl").asText());
        });
    }

    @Test void requestRemovesSasFromScalarAndListBeforePersistence() {
        runner().run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            var request = mapper.readValue("{\"images\":[\"" + URL + "\"],\"thumbnailImageUrl\":\"" + URL + "\"}", CreateNoticeRequest.class);
            assertEquals(List.of(REF), request.getImages());
            assertEquals(REF, request.getThumbnailImageUrl());
            var settings = mapper.readValue("{\"ticketingBackgroundImageUrl\":\"" + URL + "\"}", UpdateFestivalTicketingSettingsRequest.class);
            assertEquals(REF, settings.getTicketingBackgroundImageUrl());
            assertTrue(settings.isBackgroundImageSpecified());
            try (var factory = Validation.buildDefaultValidatorFactory()) {
                assertTrue(factory.getValidator().validateProperty(settings, "ticketingBackgroundImageUrl").isEmpty());
            }
            var clear = mapper.readValue("{\"ticketingBackgroundImageUrl\":null}", UpdateFestivalTicketingSettingsRequest.class);
            assertTrue(clear.isBackgroundImageSpecified());
            assertNull(clear.getTicketingBackgroundImageUrl());
        });
    }

    @Test void bundleAndPreviewRequestsNormalizeReadSasBeforeComparisonOrPersistence() {
        runner().run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            String bundleJson = ("""
                    {"name":"Azure","themeData":{"brand":{"primary":"#112233","secondary":"#112233","accent":"#112233","strong":"#112233"},
                    "status":{"success":"#112233","warning":"#112233","danger":"#112233","pending":"#112233"},
                    "category":{"pub":"#112233","foodTruck":"#112233","experience":"#112233","event":"#112233","restArea":"#112233","facility":"#112233"}},
                    "sourcePosterUrl":"%s","sourceColors":["#112233"],"selectedBaseColor":"#112233",
                    "ticketCardBackgroundImageUrl":"%s","ticketingBackgroundImageUrl":"%s",
                    "homeBanner":{"imageUrl":"%s","expectedFirstImageUrl":"%s"}}""").formatted(URL, URL, URL, URL, URL);
            ThemeBundleRequest bundle = mapper.readValue(bundleJson, ThemeBundleRequest.class);
            assertEquals(REF, bundle.sourcePosterUrl());
            assertEquals(REF, bundle.ticketCardBackgroundImageUrl());
            assertEquals(REF, bundle.ticketingBackgroundImageUrl());
            assertEquals(REF, bundle.homeBanner().imageUrl());
            assertEquals(REF, bundle.homeBanner().expectedFirstImageUrl());

            HomeBannerPreviewRequest preview = mapper.readValue(
                    "{\"sourcePosterUrl\":\"" + URL + "\",\"layout\":\"cover\"}", HomeBannerPreviewRequest.class);
            assertEquals(REF, preview.sourcePosterUrl());
        });
    }

    @Test void azureFacadesStartWithoutNhnClientsOrPropertiesAndStoreOnlyReference() {
        runner().withPropertyValues("image.storage.provider=azure")
                .withUserConfiguration(S3Config.class, S3Uploader.class, S3PosterImageReader.class,
                        S3PresignService.class, S3PathGenerator.class)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertTrue(context.getBeansOfType(software.amazon.awssdk.services.s3.S3Client.class).isEmpty());
                    when(storage.upload(anyString(), any())).thenReturn(REF);
                    var result = context.getBean(S3Uploader.class).uploadNoticeImage(
                            new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1, 2}));
                    assertEquals(REF, result.url());
                    var mapper = context.getBean(ObjectMapper.class);
                    assertEquals(URL, mapper.readTree(mapper.writeValueAsString(result)).get("url").asText());
                });
    }

    @Test void azurePresignEndpointsAreRejectedBeforeAnyStorageSdkCall() {
        runner().withUserConfiguration(S3PresignService.class, S3PathGenerator.class).run(context -> {
            var presign = context.getBean(S3PresignService.class);
            assertAzurePresignRejected(() -> presign.presignPutNoticeImage("image.png", "image/png", 10L));
            assertAzurePresignRejected(() -> presign.presignPutAdImage("image.png", "image/png", 10L));
            assertAzurePresignRejected(() -> presign.presignPutArtistImage(1, "image.png", "image/png", 10L));
            assertAzurePresignRejected(() -> presign.presignPutPubImage("college", "department", "image.png", "image/png", 10L));
            verifyNoInteractions(storage);
        });
    }

    private static void assertAzurePresignRejected(Runnable call) {
        var error = assertThrows(org.springframework.web.server.ResponseStatusException.class, call::run);
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertEquals("Azure 이미지 업로드는 multipart API를 사용해 주세요", error.getReason());
    }

    @Test void noAzureConfigurationPreservesExistingUrlContract() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .run(context -> {
                    var mapper = context.getBean(ObjectMapper.class);
                    var result = new S3UploadResult("key", "https://legacy.example/image.png");
                    assertEquals(result.url(), mapper.readTree(mapper.writeValueAsString(result)).get("url").asText());
                });
    }

    @Test void azureThumbnailDoesNotInventMissingObjectOrInvalidateSignature() {
        assertEquals(REF, ThumbnailUrlResolver.toThumbnailUrl(REF));
        assertEquals(URL, ThumbnailUrlResolver.toThumbnailUrl(URL));
    }
}
