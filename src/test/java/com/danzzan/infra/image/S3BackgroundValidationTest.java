package com.danzzan.infra.image;

import com.danzzan.infra.s3.S3PosterImageReader;
import com.danzzan.infra.s3.S3Uploader;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Utilities;
import software.amazon.awssdk.services.s3.model.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class S3BackgroundValidationTest {
    final S3Client client = mock(S3Client.class);
    final S3Uploader uploader = new S3Uploader(client);
    final ImageStorage storage = new S3ImageStorage(uploader, mock(S3PosterImageReader.class));

    void configure(String publicBase, String swift) {
        ReflectionTestUtils.setField(uploader, "bucket", "test-bucket");
        ReflectionTestUtils.setField(uploader, "endpoint", "https://objects.example.com");
        ReflectionTestUtils.setField(uploader, "publicBaseUrl", publicBase);
        ReflectionTestUtils.setField(uploader, "swiftAuthPath", swift);
        when(client.utilities()).thenReturn(S3Utilities.builder().region(Region.AP_NORTHEAST_2)
                .build());
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().contentLength(100L).build());
    }
    String upload() {
        return storage.uploadTicketingBackground(1L,
                new MockMultipartFile("file", "bg.jpg", "image/jpeg", new byte[]{1, 2})).url();
    }
    @Test void acceptsExactUploadedUrlsForCdnSwiftAndSdkPaths() {
        for (String[] config : new String[][]{{"https://cdn.example.com/images", ""}, {"", "v1/AUTH_test"}, {"", ""}}) {
            configure(config[0], config[1]);
            String url = upload();
            assertDoesNotThrow(() -> storage.validateTicketingBackground(1L, url));
            verify(client, atLeastOnce()).headObject(argThat((HeadObjectRequest request) ->
                    request.bucket().equals("test-bucket") && url.endsWith("/" + request.key())
                            && request.key().startsWith("festival/ticketing-background/")));
        }
    }
    @Test void rejectsExternalOriginsOtherDirectoriesQueriesAndTraversalBeforeObjectLookup() {
        configure("https://cdn.example.com/images", "");
        String url = upload();
        clearInvocations(client);
        for (String bad : new String[]{url.replace("cdn.example.com", "evil.example"),
                url.replace("/images/", "/other/"), url.replace("ticketing-background", "1/poster"),
                url + "?x=1", url + "#fragment", url.replace("/festival/", "/%2e%2e/festival/"),
                url.replace("https://", "http://"), url.replace("cdn.example.com", "cdn.example.com@evil.example"),
                "https://evil.example/" + url, "not-a-url"}) {
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                    () -> storage.validateTicketingBackground(1L, bad)).getStatusCode(), bad);
        }
        verify(client, never()).headObject(any(HeadObjectRequest.class));
    }
    @Test void boundsVerificationAttemptsAndTotalTimeAndMapsTimeoutToBadGateway() {
        configure("https://cdn.example.com/images", "");
        String url = upload();
        when(client.headObject(any(HeadObjectRequest.class))).thenAnswer(invocation -> {
            HeadObjectRequest request = invocation.getArgument(0);
            var config = request.overrideConfiguration().orElseThrow(() -> new AssertionError("Missing bounded HEAD timeout"));
            assertTrue(config.apiCallTimeout().orElseThrow().compareTo(java.time.Duration.ofSeconds(5)) <= 0);
            assertTrue(config.apiCallAttemptTimeout().orElseThrow().compareTo(java.time.Duration.ofSeconds(2)) <= 0);
            throw software.amazon.awssdk.core.exception.ApiCallTimeoutException.create(5000);
        });
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(ResponseStatusException.class,
                () -> storage.validateTicketingBackground(1L, url)).getStatusCode());
    }

    @Test void acceptsOnlyOwnedExistingHomeObjectsForAllPublicUrlModes() {
        for (String[] config : new String[][]{{"https://cdn.example.com/images", ""}, {"", "v1/AUTH_test"}, {"", ""}}) {
            configure(config[0], config[1]);
            String url = storage.uploadHomeImage(1L, new MockMultipartFile("file", "home.jpg", "image/jpeg", new byte[]{1, 2}));
            assertDoesNotThrow(() -> storage.validateHomeImage(1L, url));
            for (String bad : new String[]{url.replace("/1/home/", "/2/home/"), url.replace("/home/", "/poster/"),
                    url + "?x=1", url + "#x", "https://external.example/" + url.substring(url.indexOf("festival/"))}) {
                assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                        () -> storage.validateHomeImage(1L, bad)).getStatusCode());
            }
            when(client.headObject(any(HeadObjectRequest.class))).thenThrow(NoSuchKeyException.builder().statusCode(404).build());
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                    () -> storage.validateHomeImage(1L, url)).getStatusCode());
            reset(client);
        }
    }

    @Test void rejectsMissingObjectAndDistinguishesStorageFailure() {
        configure("https://cdn.example.com/images", "");
        String url = upload();
        when(client.headObject(any(HeadObjectRequest.class))).thenThrow(NoSuchKeyException.builder().statusCode(404).build());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> storage.validateTicketingBackground(1L, url)).getStatusCode());
        when(client.headObject(any(HeadObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(503).build());
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(ResponseStatusException.class,
                () -> storage.validateTicketingBackground(1L, url)).getStatusCode());
    }
}
