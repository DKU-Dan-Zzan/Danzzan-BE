package com.danzzan.infra.s3;

import com.danzzan.domain.festival.service.TicketingBackgroundService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.S3Client;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminImageUploadPolicyTest {
    private final S3Client storage = mock(S3Client.class);
    private final S3Uploader uploader = new S3Uploader(storage);

    private List<Function<MultipartFile, S3UploadResult>> adminUploads() {
        return List.of(uploader::uploadNoticeImage, uploader::uploadAdImage,
                file -> uploader.uploadPubImage("school", "department", file),
                file -> uploader.uploadArtistImage(1L, file),
                file -> uploader.uploadFestivalPoster(1L, file),
                file -> uploader.uploadFestivalLogo(1L, file));
    }

    @Test void allAdminUploadPathsRejectAboveFiveMbBeforeReadingOrStoring() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(5L * 1024 * 1024 + 1);
        for (var upload : adminUploads()) {
            var error = assertThrows(ResponseStatusException.class, () -> upload.apply(file));
            assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        }
        verify(file, never()).getBytes();
        verifyNoInteractions(storage);
    }

    @Test void allAdminUploadPathsAcceptExactlyFiveMb() {
        configureStorage();
        var file = new MockMultipartFile("file", "image.png", "image/png", new byte[5 * 1024 * 1024]);
        for (var upload : adminUploads()) assertNotNull(upload.apply(file).url());
    }

    @Test void ticketingBackgroundStillAcceptsImageAboveFiveMb() throws Exception {
        configureStorage();
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        var file = new MockMultipartFile("file", "background.png", "image/png",
                Arrays.copyOf(bytes.toByteArray(), 6 * 1024 * 1024));
        assertNotNull(new TicketingBackgroundService(new com.danzzan.infra.image.S3ImageStorage(uploader, mock(S3PosterImageReader.class))).upload(file).url());
    }

    private void configureStorage() {
        ReflectionTestUtils.setField(uploader, "bucket", "test-bucket");
        ReflectionTestUtils.setField(uploader, "publicBaseUrl", "https://cdn.example.com");
    }
}
