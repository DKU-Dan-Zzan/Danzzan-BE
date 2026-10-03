package com.danzzan.infra.s3;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class S3PosterImageReaderTest {
    private final S3Client storage = mock(S3Client.class);
    private final S3PosterImageReader reader = new S3PosterImageReader(storage);
    private static final String POSTER = "https://cdn.example.com/bucket/festival/1/poster/20261003/123e4567-e89b-12d3-a456-426614174000_poster.png";

    @Test void acceptsOnlyStoredPosterKeyOfTheRequestedFestival() {
        assertEquals("festival/1/poster/20261003/123e4567-e89b-12d3-a456-426614174000_poster.png", reader.objectKey(1L, POSTER));
        assertThrows(ResponseStatusException.class, () -> reader.objectKey(2L, POSTER));
        assertThrows(ResponseStatusException.class, () -> reader.objectKey(1L, "https://unrelated.example.com/logo.png"));
        verifyNoInteractions(storage);
    }

    @Test void storageFailureBecomesGatewayError() {
        ReflectionTestUtils.setField(reader, "bucket", "test-bucket");
        when(storage.getObject(any(GetObjectRequest.class))).thenThrow(SdkClientException.create("storage unavailable"));
        var error = assertThrows(ResponseStatusException.class, () -> reader.read(1L, POSTER));
        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
    }
}
