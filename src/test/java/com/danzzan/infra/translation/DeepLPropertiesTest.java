package com.danzzan.infra.translation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepLPropertiesTest {

    @Test
    void 무료키는_api_free_엔드포인트를_사용한다() {
        DeepLProperties properties = new DeepLProperties();
        properties.setApiKey("not-a-real-key-free:fx");

        assertEquals("https://api-free.deepl.com/v2", properties.resolveApiUrl());
    }

    @Test
    void 유료키는_api_엔드포인트를_사용한다() {
        DeepLProperties properties = new DeepLProperties();
        properties.setApiKey("not-a-real-key-paid");

        assertEquals("https://api.deepl.com/v2", properties.resolveApiUrl());
    }

    @Test
    void apiUrl을_명시하면_자동판별보다_우선한다() {
        DeepLProperties properties = new DeepLProperties();
        properties.setApiKey("not-a-real-key-free:fx");
        properties.setApiUrl("https://custom.example.com/v2");

        assertEquals("https://custom.example.com/v2", properties.resolveApiUrl());
    }

    @Test
    void 키가_없으면_설정되지_않은_것으로_판단한다() {
        DeepLProperties properties = new DeepLProperties();

        assertFalse(properties.isConfigured());

        properties.setApiKey("   ");
        assertFalse(properties.isConfigured());

        properties.setApiKey("not-a-real-key-free:fx");
        assertTrue(properties.isConfigured());
    }
}
