package com.danzzan.infra.image;

import com.danzzan.domain.festival.service.PosterColorExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalImageStorageTest {
    @TempDir Path directory;

    @Test
    void uploadsReadsAndAnalyzesPosterWithoutOverwritingOlderVersion() throws Exception {
        LocalImageStorage storage = new LocalImageStorage(directory.toString(), "http://localhost:8080/uploads");
        MockMultipartFile first = image(Color.RED);
        MockMultipartFile second = image(Color.BLUE);

        String posterA = storage.uploadPoster(1L, first);
        String posterB = storage.uploadPoster(1L, second);
        assertNotEquals(posterA, posterB);
        assertTrue(posterA.matches("http://localhost:8080/uploads/festival/1/poster/[0-9]{8}/[a-f0-9-]{36}_poster.png"));
        assertArrayEquals(first.getBytes(), storage.readPoster(1L, posterA));
        assertArrayEquals(second.getBytes(), storage.readPoster(1L, posterB));
        assertTrue(Files.exists(directory.resolve(storage.posterKey(1L, posterA))));
        assertTrue(new PosterColorExtractor().extract(storage.readPoster(1L, posterA)).colors().stream()
                .anyMatch(candidate -> candidate.hex().equals("#FF0000")));
        assertTrue(Files.exists(directory.resolve(storage.posterKey(1L, posterB))));
    }

    @Test
    void logoUsesSameUrlContractButCannotBeReadAsPoster() throws Exception {
        LocalImageStorage storage = new LocalImageStorage(directory.toString(), "http://localhost:8080/uploads");
        String logo = storage.uploadLogo(2L, image(Color.GREEN));
        assertTrue(logo.contains("/festival/2/logo/"));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY,
                assertThrows(ResponseStatusException.class, () -> storage.readPoster(2L, logo)).getStatusCode());
    }

    @Test
    void rejectsOtherFestivalsOriginsAndTraversal() throws Exception {
        LocalImageStorage storage = new LocalImageStorage(directory.toString(), "http://localhost:8080/uploads");
        String poster = storage.uploadPoster(1L, image(Color.RED));
        for (String invalid : new String[]{
                poster.replace("localhost:8080", "evil.example.com"),
                poster.replace("/festival/1/", "/festival/2/"),
                "http://localhost:8080/uploads/festival/1/poster/../logo/secret.png",
                "http://localhost:8080/uploads/festival/1/poster/%2e%2e/secret.png",
                poster + "?other=1"
        }) {
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY,
                    assertThrows(ResponseStatusException.class, () -> storage.readPoster(1L, invalid)).getStatusCode());
        }
    }

    private MockMultipartFile image(Color color) throws Exception {
        BufferedImage picture = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < picture.getHeight(); y++) for (int x = 0; x < picture.getWidth(); x++) {
            picture.setRGB(x, y, color.getRGB());
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(picture, "png", output);
        return new MockMultipartFile("file", "poster.png", "image/png", output.toByteArray());
    }
}
