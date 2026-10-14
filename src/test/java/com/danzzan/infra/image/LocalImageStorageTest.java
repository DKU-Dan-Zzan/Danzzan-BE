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

    @Test
    void validatesOnlyExistingOwnBackgroundUploadsAndRejectsPathEscapes() throws Exception {
        var storage = new LocalImageStorage(directory.toString(), "http://localhost:8080/uploads");
        var uploaded = storage.uploadTicketingBackground(1L, image(Color.RED));
        assertDoesNotThrow(() -> storage.validateTicketingBackground(1L, uploaded.url()));
        for (String invalid : new String[]{uploaded.url().replace("localhost:8080", "evil.example"),
                uploaded.url().replace("/1/", "/2/"), uploaded.url() + "?x=1", uploaded.url() + "#x",
                uploaded.url().replace("/festival/", "/../festival/"),
                uploaded.url().replace("/festival/", "/%2e%2e/festival/"),
                uploaded.url().replace("ticketing-background", "poster"),
                uploaded.url().replace("http://", "https://")}) {
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                    () -> storage.validateTicketingBackground(1L, invalid)).getStatusCode());
        }
        Files.delete(directory.resolve(uploaded.key()));
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> storage.validateTicketingBackground(1L, uploaded.url())).getStatusCode());
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
    @org.junit.jupiter.api.Test
    void ticketingBackgroundAcceptsTenMbAndRetainsLocalUrlContract(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var storage = new LocalImageStorage(directory.toString(), "http://localhost:8080/uploads");
        var file = new org.springframework.mock.web.MockMultipartFile("file", "background.jpg", "image/jpeg", new byte[10 * 1024 * 1024]);
        var result = storage.uploadTicketingBackground(1L, file);
        org.junit.jupiter.api.Assertions.assertTrue(result.key().startsWith("festival/1/ticketing-background/"));
        org.junit.jupiter.api.Assertions.assertEquals("http://localhost:8080/uploads/" + result.key(), result.url());
        org.junit.jupiter.api.Assertions.assertEquals(file.getSize(), java.nio.file.Files.size(directory.resolve(result.key())));
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> storage.uploadTicketingBackground(1L, new org.springframework.mock.web.MockMultipartFile("file", new byte[10 * 1024 * 1024 + 1])));
    }
}
