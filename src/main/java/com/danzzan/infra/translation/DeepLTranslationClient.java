package com.danzzan.infra.translation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DeepLTranslationClient implements TranslationClient {

    private static final String SOURCE_LANG = "KO";
    private static final String TARGET_LANG = "EN-US";

    private final DeepLProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public List<String> translate(List<String> texts, String glossaryId) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        if (!properties.isConfigured()) {
            throw new TranslationUnavailableException("DEEPL_API_KEY가 설정되지 않았습니다.");
        }

        String rawResponse;
        try {
            rawResponse = WebClient.builder()
                    .baseUrl(properties.resolveApiUrl())
                    .defaultHeader(HttpHeaders.AUTHORIZATION,
                            "DeepL-Auth-Key " + properties.getApiKey().trim())
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build()
                    .post()
                    .uri("/translate")
                    .bodyValue(buildRequestBody(texts, glossaryId))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (Exception e) {
            throw new TranslationUnavailableException("DeepL 호출에 실패했습니다.", e);
        }

        return parseTranslations(rawResponse, texts.size());
    }

    private String buildRequestBody(List<String> texts, String glossaryId) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode textArray = root.putArray("text");
        texts.forEach(textArray::add);
        root.put("source_lang", SOURCE_LANG);
        root.put("target_lang", TARGET_LANG);
        if (glossaryId != null && !glossaryId.isBlank()) {
            root.put("glossary_id", glossaryId);
        }
        return root.toString();
    }

    private List<String> parseTranslations(String rawResponse, int expectedSize) {
        try {
            JsonNode translations = objectMapper.readTree(rawResponse).path("translations");
            List<String> result = new ArrayList<>(expectedSize);
            translations.forEach(node -> result.add(node.path("text").asText()));

            if (result.size() != expectedSize) {
                throw new TranslationUnavailableException(
                        "DeepL 응답 개수가 요청과 다릅니다. 요청=" + expectedSize + ", 응답=" + result.size());
            }
            return result;
        } catch (TranslationUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new TranslationUnavailableException("DeepL 응답 파싱에 실패했습니다.", e);
        }
    }
}
