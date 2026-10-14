package com.danzzan.domain.festival.background;

import com.danzzan.domain.festival.theme.exception.ThemeRecommendationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class OpenAiPosterFocusClientTest {
    final ObjectMapper mapper = new ObjectMapper();
    final OpenAiPosterFocusClient client = new OpenAiPosterFocusClient(mapper, "", "gpt-4o-mini", "https://api.openai.com/v1/responses");
    String envelope(String text) throws Exception {
        return mapper.writeValueAsString(Map.of("status", "completed", "output", List.of(Map.of("content", List.of(Map.of("type", "output_text", "text", text))))));
    }
    @Test void validatesModelResponseWithoutCoercionOrSilentCenterCrop() throws Exception {
        var focus = client.parseResponse(envelope("{\"x\":0,\"y\":1,\"reason\":\"포스터의 여백\"}"));
        assertEquals(0, focus.x()); assertEquals(1, focus.y());
        for (String invalid : new String[]{"{}", "null", "{\"x\":\"0.5\",\"y\":0.5,\"reason\":\"r\"}",
                "{\"x\":-0.1,\"y\":0.5,\"reason\":\"r\"}", "{\"x\":0.5,\"y\":1.1,\"reason\":\"r\"}",
                "{\"x\":0.5,\"y\":0.5,\"reason\":\" \"}", "not json"}) {
            String body = envelope(invalid);
            assertEquals("OPENAI_MALFORMED_OUTPUT", assertThrows(ThemeRecommendationException.class, () -> client.parseResponse(body)).getCode());
        }
        for (String invalid : new String[]{"null", "not json", "{\"status\":\"incomplete\"}", "{\"status\":\"completed\",\"output\":[]}"})
            assertThrows(ThemeRecommendationException.class, () -> client.parseResponse(invalid));
    }
    @Test void missingKeyFailsBeforeNetwork() {
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ThemeRecommendationException.class,
                () -> client.recommend(new byte[]{(byte)0xff}, "ticket")).getStatus());
    }
    @Test void sendsActualImageToResponsesAndMapsUpstreamErrorsWithoutLeakingBody() throws Exception {
        try (var server = new MockWebServer()) {
            server.start();
            var transport = new OpenAiPosterFocusClient(mapper, "test-secret", "existing-model", server.url("/v1/responses").toString());
            server.enqueue(new MockResponse().setBody(envelope("{\"x\":0.3,\"y\":0.7,\"reason\":\"여백\"}")));
            assertEquals(.3, transport.recommend(new byte[]{(byte)0xff, (byte)0xd8, (byte)0xff}, "ticket").x());
            var request = server.takeRequest();
            var body = mapper.readTree(request.getBody().readUtf8());
            assertEquals("existing-model", body.path("model").asText());
            assertFalse(body.path("store").asBoolean());
            assertTrue(body.toString().contains("data:image/jpeg;base64,"));
            assertTrue(body.toString().contains("400:180"));
            assertTrue(body.path("text").path("format").path("strict").asBoolean());
            for (int status : new int[]{429, 401, 500}) {
                server.enqueue(new MockResponse().setResponseCode(status).setBody("secret-upstream-body"));
                var error = assertThrows(ThemeRecommendationException.class, () -> transport.recommend(new byte[]{(byte)0xff}, "closed"));
                assertEquals(status == 429 ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_GATEWAY, error.getStatus());
                assertFalse(error.getMessage().contains("secret"));
            }
        }
    }
}
