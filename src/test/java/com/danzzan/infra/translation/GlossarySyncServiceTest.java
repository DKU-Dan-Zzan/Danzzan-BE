package com.danzzan.infra.translation;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GlossarySyncServiceTest {

    private MockWebServer server;
    private DeepLProperties properties;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();

        properties = new DeepLProperties();
        properties.setApiKey("test-key:fx");
        properties.setApiUrl(server.url("/v2").toString());
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void 같은_이름의_용어집이_있으면_삭제하고_새로_만든다() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"glossaries":[
                          {"glossary_id":"old-id","name":"danzzan-festival-ko-en","ready":true}
                        ]}
                        """));
        server.enqueue(new MockResponse().setResponseCode(204));
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"glossary_id":"new-id","name":"danzzan-festival-ko-en","ready":true}
                        """));

        GlossarySyncService service =
                new GlossarySyncService(properties, new ObjectMapper());
        service.sync();

        assertEquals("new-id", service.getGlossaryId());
        assertEquals(3, server.getRequestCount());
    }

    @Test
    void 기존_용어집이_없으면_바로_생성한다() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"glossaries":[]}
                        """));
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"glossary_id":"fresh-id","name":"danzzan-festival-ko-en","ready":true}
                        """));

        GlossarySyncService service =
                new GlossarySyncService(properties, new ObjectMapper());
        service.sync();

        assertEquals("fresh-id", service.getGlossaryId());
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void 동기화가_실패해도_예외를_던지지_않고_null을_유지한다() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

        GlossarySyncService service =
                new GlossarySyncService(properties, new ObjectMapper());
        service.sync();

        assertNull(service.getGlossaryId());
    }

    @Test
    void 키가_없으면_호출하지_않고_null을_유지한다() {
        DeepLProperties empty = new DeepLProperties();
        GlossarySyncService service =
                new GlossarySyncService(empty, new ObjectMapper());
        service.sync();

        assertNull(service.getGlossaryId());
        assertEquals(0, server.getRequestCount());
    }
}
