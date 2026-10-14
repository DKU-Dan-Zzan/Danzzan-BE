package com.danzzan.infra.azure;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.UserDelegationKey;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import org.mockito.ArgumentCaptor;

class AzureBlobStorageTest {
    private static final Instant NOW = Instant.parse("2026-10-12T04:00:00Z");

    private BlobServiceClient serviceClient;
    private BlobContainerClient containerClient;
    private BlobClient blobClient;
    private AzureBlobStorage storage;

    @BeforeEach
    void setUp() {
        serviceClient = mock(BlobServiceClient.class);
        containerClient = mock(BlobContainerClient.class);
        blobClient = mock(BlobClient.class);
        when(serviceClient.getBlobContainerClient("images")).thenReturn(containerClient);
        when(containerClient.getBlobClient(anyString())).thenReturn(blobClient);
        storage = new AzureBlobStorage(serviceClient, properties(Duration.ofMinutes(5)),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void normalizesOnlyConfiguredReferencesAndBlobUrls() {
        assertEquals("azure:images/notices/20261012/image.png",
                storage.normalizeReference("azure:images/notices/20261012/image.png"));
        assertEquals("azure:images/festival/1/poster/한글.png",
                storage.normalizeReference("https://danzzandemo2026.blob.core.windows.net/images/festival/1/poster/%ED%95%9C%EA%B8%80.png?sv=x&sig=expired"));
        assertEquals("https://legacy.example/image.png?cache=1", storage.normalizeReference("https://legacy.example/image.png?cache=1"));
        assertEquals("", storage.normalizeReference(""));
        assertEquals(null, storage.normalizeReference(null));
    }

    @Test
    void normalizesSdkEncodedBlobPathSeparatorsWithoutAllowingTraversal() {
        var sdkClient = new com.azure.storage.blob.BlobServiceClientBuilder()
                .endpoint("https://danzzandemo2026.blob.core.windows.net").buildClient();
        String url = sdkClient.getBlobContainerClient("images")
                .getBlobClient("notices/images/preflight.png").getBlobUrl();
        assertEquals("azure:images/notices/images/preflight.png", storage.normalizeReference(url + "?sig=expired"));
        assertBadReference("https://danzzandemo2026.blob.core.windows.net/images/notices%2F..%2Fprivate.png");
        assertBadReference("https://danzzandemo2026.blob.core.windows.net/images/notices%252Fprivate.png");
        assertBadReference("https://danzzandemo2026.blob.core.windows.net/images/notices%2F%2Fprivate.png");
    }

    @Test
    void rejectsForeignOrTraversingAzureReferences() {
        assertBadReference("https://other.blob.core.windows.net/images/a.png?sig=token");
        assertBadReference("https://danzzandemo2026.blob.core.windows.net/other/a.png");
        assertBadReference("https://danzzandemo2026.blob.core.windows.net/images/a/%2e%2e/private.png");
        assertBadReference("azure:images/a/../private.png");
        assertBadReference("azure:images/a%252e%252e/private.png");
        assertBadReference("https://example.invalid/image.png?sig=token");
    }

    @Test
    void issuesFullHttpsReadUrlWithReadOnlyFiveMinuteSasAndCachesDelegationKey() {
        stubDelegationKey();
        when(blobClient.getBlobUrl()).thenReturn("https://danzzandemo2026.blob.core.windows.net/images/a.png");
        when(blobClient.generateUserDelegationSas(any(BlobServiceSasSignatureValues.class), any(UserDelegationKey.class)))
                .thenReturn("sp=r&sig=redacted");

        assertEquals("https://danzzandemo2026.blob.core.windows.net/images/a.png?sp=r&sig=redacted",
                storage.readUrl("azure:images/a.png"));
        storage.readUrl("azure:images/a.png");

        ArgumentCaptor<BlobServiceSasSignatureValues> values = ArgumentCaptor.forClass(BlobServiceSasSignatureValues.class);
        verify(blobClient, times(2)).generateUserDelegationSas(values.capture(), any(UserDelegationKey.class));
        BlobSasPermission permission = BlobSasPermission.parse(values.getAllValues().get(0).getPermissions());
        assertTrue(permission.hasReadPermission());
        assertFalse(permission.hasWritePermission());
        assertEquals(NOW.plusSeconds(300), values.getAllValues().get(0).getExpiryTime().toInstant());
        assertEquals("https", values.getAllValues().get(0).getProtocol().toString());
        verify(serviceClient, times(1)).getUserDelegationKey(any(), any());
    }

    @Test
    void uploadUsesImageContentTypeAndNeverOverwrites() {
        MockMultipartFile file = new MockMultipartFile("file", "notice.png", "image/png", new byte[]{1, 2, 3});

        assertEquals("azure:images/notices/a.png", storage.upload("notices/a.png", file));

        ArgumentCaptor<BlobParallelUploadOptions> options = ArgumentCaptor.forClass(BlobParallelUploadOptions.class);
        verify(blobClient).uploadWithResponse(options.capture(), isNull(), isNull());
        assertEquals("image/png", options.getValue().getHeaders().getContentType());
        assertEquals("*", options.getValue().getRequestConditions().getIfNoneMatch());
        assertBadRequest(() -> storage.upload("notices/a.png",
                new MockMultipartFile("file", "bad.gif", "image/gif", new byte[]{1})));
        assertBadRequest(() -> storage.upload("notices/a.png",
                new MockMultipartFile("file", new byte[0])));
    }

    @Test
    void posterReadRejectsOversizedObjectsBeforeOpeningStream() {
        BlobProperties properties = mock(BlobProperties.class);
        when(blobClient.getProperties()).thenReturn(properties);
        when(properties.getBlobSize()).thenReturn(5L * 1024 * 1024 + 1);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> storage.readPoster(7L, "azure:images/festival/7/poster/a.png"));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.getStatusCode());
        verify(blobClient, never()).openInputStream();
        assertThrows(ResponseStatusException.class,
                () -> storage.readPoster(7L, "azure:images/festival/8/poster/a.png"));
    }

    @Test
    void deleteOnlyAcceptsThisContainerCanonicalReference() {
        when(blobClient.deleteIfExists()).thenReturn(true);
        assertTrue(storage.delete("azure:images/a.png"));
        assertThrows(ResponseStatusException.class, () -> storage.delete("https://legacy.example/a.png"));
        assertThrows(ResponseStatusException.class, () -> storage.delete("azure:other/a.png"));
    }

    @Test
    void uploadFailureDoesNotReturnAStoredReference() {
        when(blobClient.uploadWithResponse(any(BlobParallelUploadOptions.class), isNull(), isNull()))
                .thenThrow(new com.azure.core.exception.AzureException("storage unavailable"));
        var failure = assertThrows(ResponseStatusException.class, () -> storage.upload("notices/a.png",
                new MockMultipartFile("file", "notice.png", "image/png", new byte[]{1})));
        assertEquals(HttpStatus.BAD_GATEWAY, failure.getStatusCode());
        verify(blobClient, never()).generateUserDelegationSas(any(), any());
    }

    @Test
    void validatesOnlyOwnedExistingSizedImageObjectsForThemeBackgrounds() {
        BlobProperties properties = mock(BlobProperties.class);
        when(blobClient.getProperties()).thenReturn(properties);
        when(properties.getBlobSize()).thenReturn(10L);
        when(properties.getContentType()).thenReturn("image/jpeg");

        assertDoesNotThrow(() -> storage.validateTicketingBackground(
                "azure:images/festival/ticketing-background/20261014/123e4567-e89b-12d3-a456-426614174000_background.jpg"));
        assertDoesNotThrow(() -> storage.validateHomeImage(7L,
                "azure:images/festival/7/home/20261014/123e4567-e89b-12d3-a456-426614174000_banner.jpg"));

        assertBadRequest(() -> storage.validateHomeImage(8L,
                "azure:images/festival/7/home/20261014/123e4567-e89b-12d3-a456-426614174000_banner.jpg"));
        assertBadRequest(() -> storage.validateTicketingBackground(
                "azure:images/festival/7/poster/20261014/123e4567-e89b-12d3-a456-426614174000_poster.jpg"));

        when(properties.getBlobSize()).thenReturn(10L * 1024 * 1024 + 1);
        assertBadRequest(() -> storage.validateTicketingBackground(
                "azure:images/festival/ticketing-background/20261014/123e4567-e89b-12d3-a456-426614174000_background.jpg"));
        when(properties.getBlobSize()).thenReturn(10L);
        when(properties.getContentType()).thenReturn("application/octet-stream");
        assertBadRequest(() -> storage.validateTicketingBackground(
                "azure:images/festival/ticketing-background/20261014/123e4567-e89b-12d3-a456-426614174000_background.jpg"));
    }

    @Test
    void delegationKeyRefreshesBeforeItCanShortenTheNextReadSas() {
        stubDelegationKey();
        var now = new java.util.concurrent.atomic.AtomicReference<>(NOW);
        Clock changingClock = mock(Clock.class);
        when(changingClock.instant()).thenAnswer(call -> now.get());
        storage = new AzureBlobStorage(serviceClient, properties(Duration.ofMinutes(5)), changingClock);
        when(blobClient.getBlobUrl()).thenReturn("https://danzzandemo2026.blob.core.windows.net/images/a.png");
        when(blobClient.generateUserDelegationSas(any(BlobServiceSasSignatureValues.class), any(UserDelegationKey.class)))
                .thenReturn("sp=r&sig=redacted");
        storage.readUrl("azure:images/a.png");
        now.set(NOW.plus(Duration.ofMinutes(54)));
        storage.readUrl("azure:images/a.png");
        verify(serviceClient, times(1)).getUserDelegationKey(any(), any());
        now.set(NOW.plus(Duration.ofMinutes(55)));
        storage.readUrl("azure:images/a.png");
        verify(serviceClient, times(2)).getUserDelegationKey(any(), any());
        var values = ArgumentCaptor.forClass(BlobServiceSasSignatureValues.class);
        verify(blobClient, times(3)).generateUserDelegationSas(values.capture(), any(UserDelegationKey.class));
        assertEquals(now.get().plusSeconds(300), values.getValue().getExpiryTime().toInstant());
    }

    private void stubDelegationKey() {
        when(serviceClient.getUserDelegationKey(any(), any())).thenReturn(new UserDelegationKey());
    }

    private static AzureBlobStorageProperties properties(Duration sasDuration) {
        return new AzureBlobStorageProperties(URI.create("https://danzzandemo2026.blob.core.windows.net"),
                "images", AzureBlobStorageProperties.Credential.AZURE_CLI, sasDuration);
    }

    private void assertBadReference(String value) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> storage.normalizeReference(value));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    private static void assertBadRequest(Runnable action) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }
}
