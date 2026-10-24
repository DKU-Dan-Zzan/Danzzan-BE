package com.danzzan.domain.festival.background;

import com.danzzan.domain.festival.theme.exception.ThemeRecommendationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpStatus;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class OpenAiPosterBackgroundClientTest {
    final ObjectMapper mapper = new ObjectMapper();
    final OpenAiPosterBackgroundClient client = new OpenAiPosterBackgroundClient(mapper, "", "gpt-image-1.5", "https://api.openai.com/v1/images/edits");
    String envelope(byte[] image) throws Exception {
        return mapper.writeValueAsString(Map.of("data", List.of(Map.of("b64_json", java.util.Base64.getEncoder().encodeToString(image)))));
    }
    @Test void rejectsMissingInvalidOrOversizedImageDataInsteadOfUsingThePoster() throws Exception {
        byte[] image = PosterBackgroundGenerationTest.solid(java.awt.Color.BLUE);
        assertArrayEquals(image, client.parseResponse(envelope(image)));
        for (String invalid : new String[]{"{}", "null", "not json", "{\"data\":[]}",
                "{\"data\":[{\"url\":\"https://example.com/image.jpg\"}]}",
                "{\"data\":[{\"b64_json\":\"%%%\"}]}", envelope(new byte[0]), envelope(new byte[5 * 1024 * 1024 + 1])}) {
            assertEquals("OPENAI_MALFORMED_OUTPUT", assertThrows(ThemeRecommendationException.class, () -> client.parseResponse(invalid)).getCode());
        }
    }
    @Test void missingKeyFailsBeforeNetwork() {
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ThemeRecommendationException.class,
                () -> client.recommend(new byte[]{(byte)0xff}, "ticket")).getStatus());
    }
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void logsSafeBackgroundRecommendationTransportDiagnostics(CapturedOutput output) throws Exception {
        try (var server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setResponseCode(500).setBody("SENTINEL_UPSTREAM_BODY"));
            var transport = new OpenAiPosterBackgroundClient(mapper, "sentinel-secret-key", "test-model",
                    server.url("/v1/images/edits").toString());

            assertEquals("OPENAI_MODEL_ERROR", assertThrows(ThemeRecommendationException.class,
                    () -> transport.recommend(new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff}, "closed")).getCode());

            String logs = output.getOut();
            assertTrue(logs.matches("(?s).*correlationId=[0-9a-f-]{36} operation=background target=closed.*"));
            assertTrue(logs.contains("imageBytes=3 modelConfigured=true endpointConfigured=true"));
            assertTrue(logs.contains("status=500 protocol="));
            assertTrue(logs.contains("outcome=OPENAI_MODEL_ERROR elapsedMs="));
            assertFalse(logs.contains("sentinel-secret-key"));
            assertFalse(logs.contains("SENTINEL_UPSTREAM_BODY"));
            assertFalse(logs.contains("data:image"));
            assertFalse(logs.contains("/v1/images/edits"));
        }
    }
    @Test void sendsPosterAsMultipartReferenceWithDestinationSpecificComposition() throws Exception {
        try (var server = new MockWebServer()) {
            server.start();
            var transport = new OpenAiPosterBackgroundClient(mapper, "test-secret", "configured-image-model", server.url("/v1/images/edits").toString());
            byte[] poster = PosterBackgroundGenerationTest.solid(java.awt.Color.RED);
            byte[] artwork = PosterBackgroundGenerationTest.solid(java.awt.Color.BLUE);
            for (String target : List.of("ticket", "closed")) {
                server.enqueue(new MockResponse().setBody(envelope(artwork)));
                assertArrayEquals(artwork, transport.recommend(poster, target));
                var request = server.takeRequest();
                assertEquals("/v1/images/edits", request.getPath());
                assertEquals("Bearer test-secret", request.getHeader("Authorization"));
                assertTrue(request.getHeader("Content-Type").startsWith("multipart/form-data; boundary="));
                var buffer = request.getBody();
                assertTrue(buffer.indexOf(okio.ByteString.of(poster)) >= 0, "must send the actual poster bytes");
                String body = buffer.readUtf8();
                assertTrue(body.contains("name=\"image[]\"; filename=\"poster.png\""));
                assertTrue(body.contains("configured-image-model"));
                assertTrue(body.contains("Remove ALL typography"));
                assertTrue(body.contains(target.equals("ticket") ? "1536x1024" : "1024x1536"));
                assertTrue(body.contains(target.equals("ticket") ? "dark foreground text" : "white logo and light text"));
            }
            for (int status : new int[]{429, 401, 403, 500}) {
                server.enqueue(new MockResponse().setResponseCode(status).setBody("secret-upstream-body"));
                var error = assertThrows(ThemeRecommendationException.class, () -> transport.recommend(poster, "closed"));
                assertEquals(status == 429 ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_GATEWAY, error.getStatus());
                assertEquals(status == 429 ? "OPENAI_RATE_LIMIT" : status == 401 || status == 403 ? "OPENAI_IMAGE_ACCESS_DENIED" : "OPENAI_MODEL_ERROR", error.getCode());
                assertFalse(error.getMessage().contains("secret"));
            }
        }
    }
}
