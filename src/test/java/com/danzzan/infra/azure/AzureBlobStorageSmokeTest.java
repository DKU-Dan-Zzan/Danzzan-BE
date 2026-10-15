package com.danzzan.infra.azure;

import com.azure.identity.AzureCliCredentialBuilder;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Explicitly opt-in smoke test against the private demo container. It neither lists nor changes
 * existing blobs; it creates and finally deletes one uniquely named test image.
 */
@Tag("azure-smoke")
@EnabledIfEnvironmentVariable(named = "AZURE_IMAGE_SMOKE", matches = "true")
class AzureBlobStorageSmokeTest {
    private static final String ENDPOINT = "https://danzzandemo2026.blob.core.windows.net";
    private static final byte[] TINY_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP43+DwHwAHAAK/K9fH4gAAAABJRU5ErkJggg==");

    @Test
    void uploadsReadsWithSasRejectsAnonymousAndDeletesOnlyItsOwnBlob() throws Exception {
        var fixture = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(TINY_PNG));
        assertEquals(1, fixture.getWidth());
        assertEquals(1, fixture.getHeight());
        AzureBlobStorage storage = storage(Duration.ofMinutes(5));
        String key = "smoke/" + UUID.randomUUID() + ".png";
        String reference = storage.reference(key);
        System.out.println("Azure smoke test blob: " + key);
        HttpClient http = HttpClient.newHttpClient();

        try {
            reference = storage.upload(key, new MockMultipartFile("file", "smoke.png", "image/png", TINY_PNG));
            String readUrl = storage.readUrl(reference);
            HttpResponse<byte[]> readResponse = getBytes(http, readUrl);
            assertEquals(200, readResponse.statusCode());
            assertArrayEquals(TINY_PNG, readResponse.body());
            HttpResponse<byte[]> anonymousResponse = getBytes(http, ENDPOINT + "/images/" + key);
            int anonymousStatus = anonymousResponse.statusCode();
            // Headerless requests can return 409 when the account disallows public access.
            boolean publicAccessBlocked = anonymousStatus == 409
                    && new String(anonymousResponse.body(), java.nio.charset.StandardCharsets.UTF_8)
                    .contains("<Code>PublicAccessNotPermitted</Code>");
            assertTrue(anonymousStatus == 401 || anonymousStatus == 403 || anonymousStatus == 404
                    || publicAccessBlocked, "anonymous reads must stay private: " + anonymousStatus);
            assertThrows(ResponseStatusException.class,
                    () -> storage.upload("smoke/invalid-" + UUID.randomUUID() + ".gif",
                            new MockMultipartFile("file", "invalid.gif", "image/gif", new byte[]{1})));

            AzureBlobStorage shortSasStorage = storage(Duration.ofSeconds(1));
            String shortSas = shortSasStorage.readUrl(reference);
            Thread.sleep(6_000L);
            assertEquals(403, get(http, shortSas), "one-second SAS should expire");
            assertEquals(200, get(http, storage.readUrl(reference)), "a regenerated SAS should work");

            assertTrue(storage.delete(reference));
            assertEquals(404, get(http, storage.readUrl(reference)), "the original path must be deleted");
            System.out.println("PASS: upload/read bytes, anonymous denial (" + anonymousStatus
                    + "), invalid MIME, expired SAS 403, renewed SAS 200, deleted blob 404");
        } finally {
            storage.delete(reference);
        }
    }

    private static AzureBlobStorage storage(Duration sasDuration) {
        AzureBlobStorageProperties properties = new AzureBlobStorageProperties(
                URI.create(ENDPOINT), "images", AzureBlobStorageProperties.Credential.AZURE_CLI,
                sasDuration);
        return new AzureBlobStorage(new BlobServiceClientBuilder()
                .endpoint(ENDPOINT)
                .credential(new AzureCliCredentialBuilder().build())
                .buildClient(), properties, Clock.systemUTC());
    }

    private static int get(HttpClient http, String url) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.discarding())
                .statusCode();
    }

    private static HttpResponse<byte[]> getBytes(HttpClient http, String url) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }
}
