package com.danzzan.infra.translation;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepLTranslationClientTest {

    private MockWebServer server;
    private DeepLTranslationClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();

        DeepLProperties properties = new DeepLProperties();
        properties.setApiKey("test-key:fx");
        properties.setApiUrl(server.url("/v2").toString());

        client = new DeepLTranslationClient(properties, new ObjectMapper());
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void 입력_순서대로_번역문을_반환한다() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"translations":[
                          {"text":"First","detected_source_language":"KO"},
                          {"text":"Second","detected_source_language":"KO"}
                        ]}
                        """));

        List<String> result = client.translate(List.of("첫째", "둘째"), null);

        assertEquals(List.of("First", "Second"), result);

        RecordedRequest request = server.takeRequest();
        assertEquals("/v2/translate", request.getPath());
        assertEquals("DeepL-Auth-Key test-key:fx", request.getHeader("Authorization"));
    }

    @Test
    void 용어집_ID를_전달하면_요청에_포함한다() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"translations":[{"text":"Student Pub","detected_source_language":"KO"}]}
                        """));

        client.translate(List.of("주점"), "glossary-123");

        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"glossary_id\":\"glossary-123\""));
    }

    @Test
    void 용어집_ID가_null이면_요청에_포함하지_않는다() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"translations":[{"text":"Booth","detected_source_language":"KO"}]}
                        """));

        client.translate(List.of("부스"), null);

        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertTrue(!body.contains("glossary_id"));
    }

    @Test
    void 서버가_오류를_반환하면_예외를_던진다() {
        server.enqueue(new MockResponse().setResponseCode(403).setBody("Forbidden"));

        assertThrows(TranslationUnavailableException.class,
                () -> client.translate(List.of("안녕"), null));
    }

    @Test
    void 키가_없으면_예외를_던진다() {
        DeepLProperties empty = new DeepLProperties();
        DeepLTranslationClient unconfigured =
                new DeepLTranslationClient(empty, new ObjectMapper());

        assertThrows(TranslationUnavailableException.class,
                () -> unconfigured.translate(List.of("안녕"), null));
    }

    @Test
    void 빈_목록은_호출없이_빈_목록을_반환한다() {
        assertEquals(List.of(), client.translate(List.of(), null));
        assertEquals(0, server.getRequestCount());
    }
}
