package com.danzzan.infra.translation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class GlossarySyncService {

    private static final String GLOSSARY_RESOURCE = "glossary/ko-en.tsv";

    private final DeepLProperties properties;
    private final ObjectMapper objectMapper;

    private final AtomicReference<String> glossaryId = new AtomicReference<>();

    public String getGlossaryId() {
        return glossaryId.get();
    }

    /**
     * 기동 완료 후 용어집을 동기화한다.
     * 실패해도 예외를 밖으로 던지지 않는다. 용어집 없이도 번역은 동작해야 한다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void sync() {
        if (!properties.isConfigured()) {
            log.info("DEEPL_API_KEY 미설정. 용어집 동기화를 건너뜁니다.");
            return;
        }

        try {
            String entries = readGlossaryEntries();
            deleteExistingGlossary();
            String created = createGlossary(entries);
            glossaryId.set(created);
            log.info("DeepL 용어집 동기화 완료. glossaryId={}", created);
        } catch (Exception e) {
            log.warn("DeepL 용어집 동기화에 실패했습니다. 용어집 없이 번역을 진행합니다.", e);
        }
    }

    private String readGlossaryEntries() throws Exception {
        try (var stream = new ClassPathResource(GLOSSARY_RESOURCE).getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
    }

    private void deleteExistingGlossary() {
        String listResponse = client().get()
                .uri("/glossaries")
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            JsonNode glossaries = objectMapper.readTree(listResponse).path("glossaries");
            for (JsonNode glossary : glossaries) {
                if (properties.getGlossaryName().equals(glossary.path("name").asText())) {
                    String existingId = glossary.path("glossary_id").asText();
                    client().delete()
                            .uri("/glossaries/" + existingId)
                            .retrieve()
                            .bodyToMono(Void.class)
                            .block();
                    log.info("기존 DeepL 용어집을 삭제했습니다. glossaryId={}", existingId);
                }
            }
        } catch (Exception e) {
            throw new TranslationUnavailableException("용어집 목록 조회에 실패했습니다.", e);
        }
    }

    private String createGlossary(String entries) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("name", properties.getGlossaryName());
        body.put("source_lang", "ko");
        body.put("target_lang", "en");
        body.put("entries", entries);
        body.put("entries_format", "tsv");

        String response = client().post()
                .uri("/glossaries")
                .bodyValue(body.toString())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            return objectMapper.readTree(response).path("glossary_id").asText(null);
        } catch (Exception e) {
            throw new TranslationUnavailableException("용어집 생성 응답 파싱에 실패했습니다.", e);
        }
    }

    private WebClient client() {
        return WebClient.builder()
                .baseUrl(properties.resolveApiUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION,
                        "DeepL-Auth-Key " + properties.getApiKey().trim())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
