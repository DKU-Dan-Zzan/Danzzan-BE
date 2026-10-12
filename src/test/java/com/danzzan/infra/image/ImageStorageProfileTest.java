package com.danzzan.infra.image;

import com.danzzan.infra.s3.S3PosterImageReader;
import com.danzzan.infra.s3.S3UploadResult;
import com.danzzan.infra.s3.S3Uploader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImageStorageProfileTest {
    @TempDir Path directory;

    @Test
    void defaultAndProdContinueToDelegateToExistingS3Implementation() {
        S3Uploader uploader = mock(S3Uploader.class);
        S3PosterImageReader reader = mock(S3PosterImageReader.class);
        MockMultipartFile file = new MockMultipartFile("file", "poster.png", "image/png", new byte[]{1});
        when(uploader.uploadFestivalPoster(1L, file)).thenReturn(new S3UploadResult("existing/key", "https://cdn/existing/key"));
        when(uploader.uploadFestivalLogo(1L, file)).thenReturn(new S3UploadResult("existing/logo", "https://cdn/existing/logo"));
        when(reader.read(1L, "https://cdn/existing/key")).thenReturn(new byte[]{1});
        ApplicationContextRunner runner = runner(uploader, reader);
        runner.run(context -> {
            assertTrue(context.getBean(ImageStorage.class) instanceof S3ImageStorage);
            assertFalse(context.containsBean("localImageResourceConfig"));
            assertEquals("https://cdn/existing/key", context.getBean(ImageStorage.class).uploadPoster(1L, file));
            assertEquals("https://cdn/existing/logo", context.getBean(ImageStorage.class).uploadLogo(1L, file));
            assertArrayEquals(new byte[]{1}, context.getBean(ImageStorage.class).readPoster(1L, "https://cdn/existing/key"));
        });
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod", "local-storage"))
                .run(context -> {
                    assertTrue(context.getBean(ImageStorage.class) instanceof S3ImageStorage);
                    assertFalse(context.containsBean("localImageResourceConfig"));
                });
        verify(uploader).uploadFestivalPoster(1L, file);
        verify(uploader).uploadFestivalLogo(1L, file);
        verify(reader).read(1L, "https://cdn/existing/key");
    }

    @Test
    void explicitLocalProfileSelectsOnlyLocalStorage() {
        runner(mock(S3Uploader.class), mock(S3PosterImageReader.class))
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("local-storage"))
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertTrue(context.getBean(ImageStorage.class) instanceof LocalImageStorage);
                    assertEquals(1, context.getBeansOfType(ImageStorage.class).size());
                    assertTrue(context.containsBean("localImageResourceConfig"));
                });
    }

    private ApplicationContextRunner runner(S3Uploader uploader, S3PosterImageReader reader) {
        return new ApplicationContextRunner()
                .withUserConfiguration(S3ImageStorage.class, LocalImageStorage.class, LocalImageResourceConfig.class)
                .withBean(S3Uploader.class, () -> uploader)
                .withBean(S3PosterImageReader.class, () -> reader)
                .withPropertyValues("local-image.root-directory=" + directory,
                        "local-image.public-base-url=http://localhost:8080/uploads");
    }
}
