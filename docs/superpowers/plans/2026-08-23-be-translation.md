# BE 번역 시스템 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 관리자가 콘텐츠를 등록하는 시점에 DeepL로 영문을 생성해 DB에 저장하고, 사용자 조회 API가 `?lang=en`으로 영문을 반환한다.

**Architecture:** `TranslationClient` 인터페이스 뒤에 DeepL 구현을 숨기고, `TranslationService`가 용어집 적용·실패 처리·빈 문자열 스킵을 담당한다. 각 도메인 서비스는 `TranslationService`만 의존한다. 번역 실패는 절대 쓰기 작업을 실패시키지 않으며, `_en`을 null로 남긴 뒤 스케줄러가 채운다.

**Tech Stack:** Java 17, Spring Boot 3.5.10, JPA/Hibernate, WebClient (spring-boot-starter-webflux), JUnit 5 + Mockito, MySQL 8.4

관련 스펙: [`docs/superpowers/specs/2026-08-23-fall-festival-i18n-design.md`](../specs/2026-08-23-fall-festival-i18n-design.md)

## Global Constraints

- 기존 외부 API 클라이언트 패턴을 따른다: 인터페이스 + 구현 + `@ConfigurationProperties`를 `src/main/java/com/danzzan/infra/<name>/`에 둔다 (`infra/octomo/` 참고).
- **`DeepLProperties.apiKey`에 `@NotBlank`를 붙이지 않는다.** 키가 없어도 앱이 기동해야 한다. `OctomoProperties`는 `@NotBlank`를 쓰지만 여기서는 따라하지 않는다.
- 테스트는 JUnit 5 + Mockito. `@ExtendWith(MockitoExtension.class)`, 메서드명은 한글 스네이크 표기 (`번역_실패시_null을_반환한다`).
- `application.yml`과 `application-prod.yml`은 깃허브 공개다. 실제 키 값을 절대 쓰지 않는다. `${DEEPL_API_KEY:}` 참조만 둔다.
- 무료 키는 `:fx`로 끝나고 `https://api-free.deepl.com/v2`를, 유료 키는 접미사 없이 `https://api.deepl.com/v2`를 쓴다. **키 접미사로 자동 판별한다.**
- DeepL 대상 언어 코드는 `EN-US`를 쓴다 (`EN`은 deprecated).
- 커밋 메시지는 기존 규칙을 따른다: `[DANZ-XXX] feat: 설명` 형식. 티켓 번호가 없으면 `feat: 설명`으로 쓴다.

---

### Task 1: DeepL 프로퍼티와 엔드포인트 자동 판별

**Files:**
- Create: `src/main/java/com/danzzan/infra/translation/DeepLProperties.java`
- Modify: `src/main/resources/application.yml` (파일 끝, `octomo:` 블록 다음)
- Test: `src/test/java/com/danzzan/infra/translation/DeepLPropertiesTest.java`

**Interfaces:**
- Consumes: 없음 (첫 태스크)
- Produces: `DeepLProperties#getApiKey(): String`, `DeepLProperties#getApiUrl(): String`, `DeepLProperties#resolveApiUrl(): String`, `DeepLProperties#isConfigured(): boolean`

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`src/test/java/com/danzzan/infra/translation/DeepLPropertiesTest.java`:

```java
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
```

- [ ] **Step 2: 테스트가 실패하는지 확인한다**

```bash
./gradlew test --tests "com.danzzan.infra.translation.DeepLPropertiesTest"
```

Expected: 컴파일 실패. `package com.danzzan.infra.translation does not exist`

- [ ] **Step 3: 최소 구현을 작성한다**

`src/main/java/com/danzzan/infra/translation/DeepLProperties.java`:

```java
package com.danzzan.infra.translation;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "deepl")
public class DeepLProperties {

    private static final String FREE_KEY_SUFFIX = ":fx";
    private static final String FREE_API_URL = "https://api-free.deepl.com/v2";
    private static final String PAID_API_URL = "https://api.deepl.com/v2";

    /**
     * 비어 있어도 앱은 기동한다. 키가 없으면 번역이 비활성화될 뿐이다.
     */
    private String apiKey;

    /**
     * 비워두면 키 접미사로 자동 판별한다.
     */
    private String apiUrl;

    private String glossaryName = "danzzan-festival-ko-en";

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String resolveApiUrl() {
        if (apiUrl != null && !apiUrl.isBlank()) {
            return apiUrl;
        }
        if (isConfigured() && apiKey.trim().endsWith(FREE_KEY_SUFFIX)) {
            return FREE_API_URL;
        }
        return PAID_API_URL;
    }
}
```

- [ ] **Step 4: 테스트가 통과하는지 확인한다**

```bash
./gradlew test --tests "com.danzzan.infra.translation.DeepLPropertiesTest"
```

Expected: PASS (4개 테스트)

- [ ] **Step 5: application.yml에 참조를 추가한다**

`src/main/resources/application.yml`의 `octomo:` 블록 바로 다음에 추가한다. **실제 키를 쓰지 않는다.**

```yaml
deepl:
  api-key: ${DEEPL_API_KEY:}
  api-url: ${DEEPL_API_URL:}
  glossary-name: ${DEEPL_GLOSSARY_NAME:danzzan-festival-ko-en}
```

`api-url`의 기본값을 비워두는 것이 중요하다. 값이 있으면 자동 판별이 무시된다.

- [ ] **Step 6: 앱이 키 없이도 기동하는지 확인한다**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: 커밋한다**

```bash
git add src/main/java/com/danzzan/infra/translation/DeepLProperties.java \
        src/test/java/com/danzzan/infra/translation/DeepLPropertiesTest.java \
        src/main/resources/application.yml
git commit -m "feat: DeepL 프로퍼티 및 엔드포인트 자동 판별 추가"
```

---

### Task 2: TranslationClient 인터페이스와 DeepL 구현

**Files:**
- Create: `src/main/java/com/danzzan/infra/translation/TranslationClient.java`
- Create: `src/main/java/com/danzzan/infra/translation/DeepLTranslationClient.java`
- Test: `src/test/java/com/danzzan/infra/translation/DeepLTranslationClientTest.java`

**Interfaces:**
- Consumes: `DeepLProperties#resolveApiUrl()`, `DeepLProperties#getApiKey()`, `DeepLProperties#isConfigured()` (Task 1)
- Produces:
  - `TranslationClient#translate(List<String> texts, String glossaryId): List<String>`
    - 입력 순서와 동일한 순서로 번역문을 반환한다
    - 번역 불가 시 `TranslationUnavailableException`을 던진다
  - `TranslationUnavailableException extends RuntimeException`

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`src/test/java/com/danzzan/infra/translation/DeepLTranslationClientTest.java`:

```java
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
```

- [ ] **Step 2: MockWebServer 의존성을 추가한다**

`build.gradle`의 `dependencies` 블록에서 `testImplementation 'org.springframework.security:spring-security-test'` 다음 줄에 추가한다:

```gradle
	testImplementation 'com.squareup.okhttp3:mockwebserver:4.12.0'
```

- [ ] **Step 3: 테스트가 실패하는지 확인한다**

```bash
./gradlew test --tests "com.danzzan.infra.translation.DeepLTranslationClientTest"
```

Expected: 컴파일 실패. `cannot find symbol: class DeepLTranslationClient`

- [ ] **Step 4: 예외 클래스를 작성한다**

`src/main/java/com/danzzan/infra/translation/TranslationUnavailableException.java`:

```java
package com.danzzan.infra.translation;

public class TranslationUnavailableException extends RuntimeException {

    public TranslationUnavailableException(String message) {
        super(message);
    }

    public TranslationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 5: 인터페이스를 작성한다**

`src/main/java/com/danzzan/infra/translation/TranslationClient.java`:

```java
package com.danzzan.infra.translation;

import java.util.List;

public interface TranslationClient {

    /**
     * 한국어 텍스트를 영어로 번역한다.
     *
     * @param texts      번역할 문자열 목록
     * @param glossaryId 적용할 용어집 ID. null이면 용어집 없이 번역한다.
     * @return 입력과 동일한 순서의 번역문 목록
     * @throws TranslationUnavailableException 번역을 수행할 수 없을 때
     */
    List<String> translate(List<String> texts, String glossaryId);
}
```

- [ ] **Step 6: DeepL 구현을 작성한다**

`src/main/java/com/danzzan/infra/translation/DeepLTranslationClient.java`:

```java
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
```

응답 개수 검증이 중요하다. 개수가 어긋난 채로 반환하면 A 공지의 번역이 B 공지에 저장된다.

- [ ] **Step 7: 테스트가 통과하는지 확인한다**

```bash
./gradlew test --tests "com.danzzan.infra.translation.DeepLTranslationClientTest"
```

Expected: PASS (6개 테스트)

- [ ] **Step 8: 실제 DeepL로 한 번 확인한다**

`application-local.yml`에 키가 들어있는 상태에서 실행한다.

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

기동 후 다른 터미널에서 앱 로그에 DeepL 관련 오류가 없는지 확인하고 `Ctrl+C`로 종료한다.
Expected: 기동 성공, DeepL 관련 예외 없음 (아직 호출되는 경로가 없으므로 정상)

- [ ] **Step 9: 커밋한다**

```bash
git add src/main/java/com/danzzan/infra/translation/ \
        src/test/java/com/danzzan/infra/translation/DeepLTranslationClientTest.java \
        build.gradle
git commit -m "feat: TranslationClient 인터페이스와 DeepL 구현 추가"
```

---

### Task 3: 용어집 동기화

**Files:**
- Create: `src/main/resources/glossary/ko-en.tsv`
- Create: `src/main/java/com/danzzan/infra/translation/GlossarySyncService.java`
- Test: `src/test/java/com/danzzan/infra/translation/GlossarySyncServiceTest.java`

**Interfaces:**
- Consumes: `DeepLProperties` (Task 1), `TranslationUnavailableException` (Task 2)
- Produces: `GlossarySyncService#getGlossaryId(): String` — 동기화된 용어집 ID를 반환한다. 동기화 실패 또는 키 미설정 시 `null`을 반환한다.

`getGlossaryId()`가 null을 반환해도 번역은 용어집 없이 동작해야 한다. 용어집 실패가 번역 전체를 막으면 안 된다.

- [ ] **Step 1: 용어집 TSV를 작성한다**

`src/main/resources/glossary/ko-en.tsv` — 탭 구분이며 **공백이 아니라 실제 탭 문자**여야 한다:

```
주점	Student Pub
타임테이블	Timetable
단과대	College
총학생회	Student Council
부스맵	Booth Map
뒷풀이	After-party
학생회관	Student Union Building
축제	Festival
공연	Performance
부스	Booth
```

- [ ] **Step 2: 실패하는 테스트를 작성한다**

`src/test/java/com/danzzan/infra/translation/GlossarySyncServiceTest.java`:

```java
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
```

- [ ] **Step 3: 테스트가 실패하는지 확인한다**

```bash
./gradlew test --tests "com.danzzan.infra.translation.GlossarySyncServiceTest"
```

Expected: 컴파일 실패. `cannot find symbol: class GlossarySyncService`

- [ ] **Step 4: 구현을 작성한다**

`src/main/java/com/danzzan/infra/translation/GlossarySyncService.java`:

```java
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
```

기동마다 삭제 후 재생성하는 이유는 TSV가 유일한 진실 공급원이 되게 하기 위해서다.
용어를 추가하려면 TSV만 고치고 배포하면 된다.

- [ ] **Step 5: 테스트가 통과하는지 확인한다**

```bash
./gradlew test --tests "com.danzzan.infra.translation.GlossarySyncServiceTest"
```

Expected: PASS (4개 테스트)

- [ ] **Step 6: 실제 DeepL로 동기화를 확인한다**

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

Expected: 로그에 `DeepL 용어집 동기화 완료. glossaryId=...` 출력. 확인 후 `Ctrl+C`.

- [ ] **Step 7: 커밋한다**

```bash
git add src/main/resources/glossary/ko-en.tsv \
        src/main/java/com/danzzan/infra/translation/GlossarySyncService.java \
        src/test/java/com/danzzan/infra/translation/GlossarySyncServiceTest.java
git commit -m "feat: DeepL 용어집 TSV 기반 동기화 추가"
```

---

### Task 4: TranslationService 파사드

**Files:**
- Create: `src/main/java/com/danzzan/infra/translation/TranslationService.java`
- Test: `src/test/java/com/danzzan/infra/translation/TranslationServiceTest.java`

**Interfaces:**
- Consumes: `TranslationClient#translate(List<String>, String)` (Task 2), `GlossarySyncService#getGlossaryId()` (Task 3)
- Produces:
  - `TranslationService#translate(String text): String` — 실패 시 `null` 반환, 예외를 던지지 않는다
  - `TranslationService#translateAll(List<String> texts): List<String>` — 입력과 같은 크기의 목록. 실패 시 전 항목이 `null`. 개별 입력이 null/빈 문자열이면 해당 위치도 `null`

**이 태스크가 이 계획에서 가장 중요하다.** 도메인 서비스가 번역 실패로 깨지지 않는 이유가 전부 여기 있다.

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`src/test/java/com/danzzan/infra/translation/TranslationServiceTest.java`:

```java
package com.danzzan.infra.translation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TranslationServiceTest {

    @Mock
    private TranslationClient translationClient;

    @Mock
    private GlossarySyncService glossarySyncService;

    @InjectMocks
    private TranslationService translationService;

    @Test
    void 번역에_성공하면_영문을_반환한다() {
        when(glossarySyncService.getGlossaryId()).thenReturn("g-1");
        when(translationClient.translate(List.of("안녕"), "g-1"))
                .thenReturn(List.of("Hello"));

        assertEquals("Hello", translationService.translate("안녕"));
    }

    @Test
    void 번역이_실패하면_null을_반환하고_예외를_던지지_않는다() {
        when(glossarySyncService.getGlossaryId()).thenReturn("g-1");
        when(translationClient.translate(any(), any()))
                .thenThrow(new TranslationUnavailableException("DeepL 장애"));

        assertNull(translationService.translate("안녕"));
    }

    @Test
    void 빈_문자열은_호출없이_null을_반환한다() {
        assertNull(translationService.translate(null));
        assertNull(translationService.translate(""));
        assertNull(translationService.translate("   "));

        verify(translationClient, never()).translate(any(), any());
    }

    @Test
    void 목록에서_빈_항목은_건너뛰고_나머지만_번역한다() {
        when(glossarySyncService.getGlossaryId()).thenReturn(null);
        when(translationClient.translate(List.of("첫째", "셋째"), null))
                .thenReturn(List.of("First", "Third"));

        List<String> result = translationService.translateAll(
                Arrays.asList("첫째", "", "셋째"));

        assertEquals(Arrays.asList("First", null, "Third"), result);
    }

    @Test
    void 목록_번역이_실패하면_전부_null인_같은_크기_목록을_반환한다() {
        when(glossarySyncService.getGlossaryId()).thenReturn(null);
        when(translationClient.translate(any(), any()))
                .thenThrow(new TranslationUnavailableException("DeepL 장애"));

        List<String> result = translationService.translateAll(List.of("가", "나", "다"));

        assertEquals(3, result.size());
        assertEquals(Arrays.asList(null, null, null), result);
    }

    @Test
    void 전부_빈_목록이면_호출하지_않는다() {
        List<String> result = translationService.translateAll(Arrays.asList("", null, "  "));

        assertEquals(Arrays.asList(null, null, null), result);
        verify(translationClient, never()).translate(any(), any());
    }

    @Test
    void 용어집_ID가_null이어도_번역을_수행한다() {
        when(glossarySyncService.getGlossaryId()).thenReturn(null);
        when(translationClient.translate(List.of("부스"), null))
                .thenReturn(List.of("Booth"));

        assertEquals("Booth", translationService.translate("부스"));
        verify(translationClient).translate(eq(List.of("부스")), isNull());
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인한다**

```bash
./gradlew test --tests "com.danzzan.infra.translation.TranslationServiceTest"
```

Expected: 컴파일 실패. `cannot find symbol: class TranslationService`

- [ ] **Step 3: 구현을 작성한다**

`src/main/java/com/danzzan/infra/translation/TranslationService.java`:

```java
package com.danzzan.infra.translation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationService {

    private final TranslationClient translationClient;
    private final GlossarySyncService glossarySyncService;

    /**
     * 단일 문자열을 번역한다. 실패하면 null을 반환하며 예외를 던지지 않는다.
     */
    public String translate(String text) {
        List<String> result = translateAll(List.of(text == null ? "" : text));
        return result.get(0);
    }

    /**
     * 목록을 번역한다. 반환 목록의 크기는 입력과 항상 같다.
     * 비어 있는 입력과 번역 실패는 모두 해당 위치에 null로 표시된다.
     */
    public List<String> translateAll(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        List<Integer> targetIndexes = new ArrayList<>();
        List<String> targets = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            if (text != null && !text.isBlank()) {
                targetIndexes.add(i);
                targets.add(text);
            }
        }

        List<String> result = new ArrayList<>(Collections.nCopies(texts.size(), null));
        if (targets.isEmpty()) {
            return result;
        }

        try {
            List<String> translated =
                    translationClient.translate(targets, glossarySyncService.getGlossaryId());
            for (int i = 0; i < targetIndexes.size(); i++) {
                result.set(targetIndexes.get(i), translated.get(i));
            }
        } catch (Exception e) {
            log.warn("번역에 실패했습니다. 영문을 비워둔 채로 진행합니다. 건수={}", targets.size(), e);
        }
        return result;
    }
}
```

`catch (Exception e)`로 넓게 잡는 것이 의도적이다. 어떤 예외든 도메인 쓰기 작업을 실패시켜서는 안 된다.

- [ ] **Step 4: 테스트가 통과하는지 확인한다**

```bash
./gradlew test --tests "com.danzzan.infra.translation.TranslationServiceTest"
```

Expected: PASS (7개 테스트)

- [ ] **Step 5: 커밋한다**

```bash
git add src/main/java/com/danzzan/infra/translation/TranslationService.java \
        src/test/java/com/danzzan/infra/translation/TranslationServiceTest.java
git commit -m "feat: 번역 실패를 삼키는 TranslationService 파사드 추가"
```

---

### Task 5: Notice 영문 컬럼과 마이그레이션

**Files:**
- Modify: `src/main/java/com/danzzan/domain/notice/entity/Notice.java`
- Create: `scripts/add_translation_columns.sql`
- Test: `src/test/java/com/danzzan/domain/notice/NoticeTranslationTest.java`

**Interfaces:**
- Consumes: 없음
- Produces: `Notice#getTitleEn()`, `Notice#getContentEn()`, `Notice#isEnIsManual()`, `Notice#applyTranslation(String titleEn, String contentEn)`, `Notice#applyManualTranslation(String titleEn, String contentEn)`

- [ ] **Step 1: 마이그레이션 SQL을 작성한다**

`scripts/add_translation_columns.sql`:

```sql
-- 가을 축제 영어 지원: 영문 컬럼 추가
-- 적용: mysql -u <user> -p <database> < scripts/add_translation_columns.sql

ALTER TABLE notice
    ADD COLUMN title_en VARCHAR(255) NULL,
    ADD COLUMN content_en TEXT NULL,
    ADD COLUMN en_is_manual BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE booth
    ADD COLUMN name_en VARCHAR(255) NULL,
    ADD COLUMN description_en VARCHAR(255) NULL,
    ADD COLUMN en_is_manual BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE pub
    ADD COLUMN name_en VARCHAR(255) NULL,
    ADD COLUMN intro_en VARCHAR(255) NULL,
    ADD COLUMN description_en TEXT NULL,
    ADD COLUMN department_en VARCHAR(255) NULL,
    ADD COLUMN en_is_manual BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE college
    ADD COLUMN name_en VARCHAR(255) NULL,
    ADD COLUMN en_is_manual BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE artist
    ADD COLUMN name_en VARCHAR(255) NULL,
    ADD COLUMN description_en VARCHAR(255) NULL,
    ADD COLUMN en_is_manual BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE performance
    ADD COLUMN stage_en VARCHAR(255) NULL,
    ADD COLUMN en_is_manual BOOLEAN NOT NULL DEFAULT FALSE;
```

로컬 `local` 프로파일은 `ddl-auto: update`라 이 SQL 없이도 컬럼이 생긴다.
운영은 `ddl-auto: validate`이므로 배포 전 이 SQL을 반드시 실행해야 한다.

- [ ] **Step 2: 실패하는 테스트를 작성한다**

`src/test/java/com/danzzan/domain/notice/NoticeTranslationTest.java`:

```java
package com.danzzan.domain.notice;

import com.danzzan.domain.notice.entity.Notice;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoticeTranslationTest {

    private Notice sampleNotice() {
        return Notice.create("제목", "내용", "작성자", "GENERAL",
                false, null, List.of(), false);
    }

    @Test
    void 새_공지는_영문이_비어있고_수동플래그가_꺼져있다() {
        Notice notice = sampleNotice();

        assertNull(notice.getTitleEn());
        assertNull(notice.getContentEn());
        assertFalse(notice.isEnIsManual());
    }

    @Test
    void 자동번역_적용시_수동플래그는_켜지지_않는다() {
        Notice notice = sampleNotice();

        notice.applyTranslation("Title", "Content");

        assertEquals("Title", notice.getTitleEn());
        assertEquals("Content", notice.getContentEn());
        assertFalse(notice.isEnIsManual());
    }

    @Test
    void 수동번역_적용시_수동플래그가_켜진다() {
        Notice notice = sampleNotice();

        notice.applyManualTranslation("Manual Title", "Manual Content");

        assertEquals("Manual Title", notice.getTitleEn());
        assertTrue(notice.isEnIsManual());
    }

    @Test
    void 자동번역은_수동플래그가_켜진_공지를_덮어쓰지_않는다() {
        Notice notice = sampleNotice();
        notice.applyManualTranslation("Manual Title", "Manual Content");

        notice.applyTranslation("Machine Title", "Machine Content");

        assertEquals("Manual Title", notice.getTitleEn());
        assertEquals("Manual Content", notice.getContentEn());
    }

    @Test
    void 자동번역의_null_값은_기존_영문을_지우지_않는다() {
        Notice notice = sampleNotice();
        notice.applyTranslation("Title", "Content");

        notice.applyTranslation(null, null);

        assertEquals("Title", notice.getTitleEn());
        assertEquals("Content", notice.getContentEn());
    }
}
```

- [ ] **Step 3: 테스트가 실패하는지 확인한다**

```bash
./gradlew test --tests "com.danzzan.domain.notice.NoticeTranslationTest"
```

Expected: 컴파일 실패. `cannot find symbol: method getTitleEn()`

- [ ] **Step 4: Notice 엔티티에 필드와 메서드를 추가한다**

`src/main/java/com/danzzan/domain/notice/entity/Notice.java`의 `updatedAt` 필드 선언 다음에 추가한다:

```java
    @Column(name = "title_en")
    private String titleEn;

    @Column(name = "content_en", columnDefinition = "TEXT")
    private String contentEn;

    @Column(name = "en_is_manual", nullable = false)
    private boolean enIsManual = false;
```

그리고 클래스 마지막 메서드 뒤에 추가한다:

```java
    /**
     * 기계번역 결과를 반영한다.
     * 사람이 손댄 번역(enIsManual = true)은 덮어쓰지 않는다.
     * null 값은 "번역하지 못했다"는 뜻이므로 기존 값을 지우지 않는다.
     */
    public void applyTranslation(String titleEn, String contentEn) {
        if (this.enIsManual) {
            return;
        }
        if (titleEn != null) {
            this.titleEn = titleEn;
        }
        if (contentEn != null) {
            this.contentEn = contentEn;
        }
    }

    /**
     * 관리자가 직접 입력한 번역을 반영하고 수동 플래그를 켠다.
     */
    public void applyManualTranslation(String titleEn, String contentEn) {
        this.titleEn = titleEn;
        this.contentEn = contentEn;
        this.enIsManual = true;
    }

    public String getTitleEn() {
        return titleEn;
    }

    public String getContentEn() {
        return contentEn;
    }

    public boolean isEnIsManual() {
        return enIsManual;
    }
```

`Notice`가 `@Getter`를 이미 쓰고 있다면 위 세 개의 getter는 생략한다.
클래스 상단의 Lombok 애노테이션을 먼저 확인할 것.

- [ ] **Step 5: 테스트가 통과하는지 확인한다**

```bash
./gradlew test --tests "com.danzzan.domain.notice.NoticeTranslationTest"
```

Expected: PASS (5개 테스트)

- [ ] **Step 6: 로컬 DB에 컬럼이 생기는지 확인한다**

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

기동 후 다른 터미널에서:

```bash
/Applications/Docker.app/Contents/Resources/bin/docker exec danzzan-mysql mysql -udanzzan -pdanzzan1234 danzzan -e "SHOW COLUMNS FROM notice LIKE '%_en%';"
```

Expected: `title_en`, `content_en`, `en_is_manual` 세 행이 출력된다. 확인 후 `Ctrl+C`.

- [ ] **Step 7: 커밋한다**

```bash
git add src/main/java/com/danzzan/domain/notice/entity/Notice.java \
        src/test/java/com/danzzan/domain/notice/NoticeTranslationTest.java \
        scripts/add_translation_columns.sql
git commit -m "feat: Notice 영문 컬럼과 번역 적용 메서드 추가"
```

---

### Task 6: NoticeService 번역 연결과 lang 파라미터

**Files:**
- Modify: `src/main/java/com/danzzan/domain/notice/service/NoticeService.java`
- Modify: `src/main/java/com/danzzan/domain/notice/dto/response/NoticeResponse.java`
- Modify: `src/main/java/com/danzzan/domain/notice/controller/NoticeController.java`
- Test: `src/test/java/com/danzzan/domain/notice/NoticeServiceTranslationTest.java`

**Interfaces:**
- Consumes: `TranslationService#translateAll(List<String>)` (Task 4), `Notice#applyTranslation(String, String)` (Task 5)
- Produces: `NoticeResponse#from(Notice notice, boolean english): NoticeResponse` — 기존 `from(Notice)`는 한국어 반환으로 유지한다

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`src/test/java/com/danzzan/domain/notice/NoticeServiceTranslationTest.java`:

```java
package com.danzzan.domain.notice;

import com.danzzan.domain.notice.dto.request.CreateNoticeRequest;
import com.danzzan.domain.notice.entity.Notice;
import com.danzzan.domain.notice.repository.NoticeRepository;
import com.danzzan.domain.notice.service.NoticeService;
import com.danzzan.infra.translation.TranslationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoticeServiceTranslationTest {

    @Mock
    private NoticeRepository noticeRepository;

    @Mock
    private TranslationService translationService;

    @InjectMocks
    private NoticeService noticeService;

    private CreateNoticeRequest sampleRequest() {
        CreateNoticeRequest request = new CreateNoticeRequest();
        request.setTitle("우천 안내");
        request.setContent("야외 부스 운영이 중단될 수 있습니다.");
        request.setAuthor("총학생회");
        request.setCategory("GENERAL");
        request.setIsPinned(false);
        request.setImages(List.of());
        return request;
    }

    @Test
    void 공지_생성시_영문을_함께_저장한다() {
        when(translationService.translateAll(any()))
                .thenReturn(List.of("Rain notice", "Outdoor booths may close."));
        when(noticeRepository.save(any(Notice.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        noticeService.create(sampleRequest());

        ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
        verify(noticeRepository).save(captor.capture());

        assertEquals("Rain notice", captor.getValue().getTitleEn());
        assertEquals("Outdoor booths may close.", captor.getValue().getContentEn());
    }

    @Test
    void 번역이_실패해도_공지_등록은_성공한다() {
        when(translationService.translateAll(any()))
                .thenReturn(Arrays.asList(null, null));
        when(noticeRepository.save(any(Notice.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        noticeService.create(sampleRequest());

        ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
        verify(noticeRepository).save(captor.capture());

        assertEquals("우천 안내", captor.getValue().getTitle());
        assertNull(captor.getValue().getTitleEn());
    }
}
```

두 번째 테스트가 이 계획 전체에서 가장 중요한 검증이다.
축제 당일 DeepL이 죽어도 긴급 공지가 올라가야 한다.

- [ ] **Step 2: 테스트가 실패하는지 확인한다**

```bash
./gradlew test --tests "com.danzzan.domain.notice.NoticeServiceTranslationTest"
```

Expected: 실패. `NoticeService`에 `TranslationService` 의존성이 없어 Mockito 주입이 되지 않는다.

- [ ] **Step 3: NoticeService에 번역을 연결한다**

`src/main/java/com/danzzan/domain/notice/service/NoticeService.java`의 필드 선언부에 추가한다:

```java
    private final TranslationService translationService;
```

import를 추가한다:

```java
import com.danzzan.infra.translation.TranslationService;
import java.util.List;
```

`create` 메서드를 다음으로 교체한다:

```java
    @Transactional
    public NoticeResponse create(CreateNoticeRequest request) {
        Notice notice = Notice.create(
                request.getTitle(),
                request.getContent(),
                request.getAuthor(),
                request.getCategory(),
                request.getIsPinned(),
                request.getThumbnailImageUrl(),
                request.getImages(),
                false
        );

        List<String> translated = translationService.translateAll(
                List.of(
                        request.getTitle() == null ? "" : request.getTitle(),
                        request.getContent() == null ? "" : request.getContent()
                )
        );
        notice.applyTranslation(translated.get(0), translated.get(1));

        return NoticeResponse.from(noticeRepository.save(notice));
    }
```

`update` 메서드에서는 한국어가 바뀌었을 때만 재번역한다.
`notice.setTitle(...)` 호출 **직전에** 기존 값을 보관해두고, 모든 setter 호출이 끝난 뒤에 추가한다:

```java
        boolean koreanChanged =
                !java.util.Objects.equals(previousTitle, request.getTitle())
                        || !java.util.Objects.equals(previousContent, request.getContent());

        if (koreanChanged) {
            List<String> retranslated = translationService.translateAll(
                    List.of(
                            request.getTitle() == null ? "" : request.getTitle(),
                            request.getContent() == null ? "" : request.getContent()
                    )
            );
            notice.applyTranslation(retranslated.get(0), retranslated.get(1));
        }
```

`previousTitle`, `previousContent`는 setter 호출 전에 선언한다:

```java
        String previousTitle = notice.getTitle();
        String previousContent = notice.getContent();
```

- [ ] **Step 4: 테스트가 통과하는지 확인한다**

```bash
./gradlew test --tests "com.danzzan.domain.notice.NoticeServiceTranslationTest"
```

Expected: PASS (2개 테스트)

- [ ] **Step 5: 커밋한다**

```bash
git add src/main/java/com/danzzan/domain/notice/service/NoticeService.java \
        src/test/java/com/danzzan/domain/notice/NoticeServiceTranslationTest.java
git commit -m "feat: 공지 생성/수정 시 영문 자동 번역 연결"
```

- [ ] **Step 6: NoticeResponse에 영어 분기를 추가하는 실패 테스트를 작성한다**

`src/test/java/com/danzzan/domain/notice/NoticeResponseLangTest.java`:

```java
package com.danzzan.domain.notice;

import com.danzzan.domain.notice.dto.response.NoticeResponse;
import com.danzzan.domain.notice.entity.Notice;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NoticeResponseLangTest {

    private Notice noticeWithEnglish() {
        Notice notice = Notice.create("제목", "내용", "작성자", "GENERAL",
                false, null, List.of(), false);
        notice.applyTranslation("Title", "Content");
        return notice;
    }

    @Test
    void 영어_요청시_영문_필드값을_같은_필드명으로_반환한다() {
        NoticeResponse response = NoticeResponse.from(noticeWithEnglish(), true);

        assertEquals("Title", response.getTitle());
        assertEquals("Content", response.getContent());
    }

    @Test
    void 한국어_요청시_한국어를_반환한다() {
        NoticeResponse response = NoticeResponse.from(noticeWithEnglish(), false);

        assertEquals("제목", response.getTitle());
    }

    @Test
    void 영문이_없으면_한국어로_폴백한다() {
        Notice notice = Notice.create("제목", "내용", "작성자", "GENERAL",
                false, null, List.of(), false);

        NoticeResponse response = NoticeResponse.from(notice, true);

        assertEquals("제목", response.getTitle());
        assertEquals("내용", response.getContent());
    }
}
```

- [ ] **Step 7: 테스트가 실패하는지 확인한다**

```bash
./gradlew test --tests "com.danzzan.domain.notice.NoticeResponseLangTest"
```

Expected: 컴파일 실패. `method from(Notice, boolean) not found`

- [ ] **Step 8: NoticeResponse에 오버로드를 추가한다**

`src/main/java/com/danzzan/domain/notice/dto/response/NoticeResponse.java`의 기존 `from(Notice)` 메서드 아래에 추가한다.
**기존 `from(Notice)`는 지우지 않는다.** 관리자 경로가 계속 사용한다.

```java
    /**
     * 언어에 따라 값을 골라 담는다. 필드명은 바뀌지 않는다.
     * 영문이 비어 있으면 한국어로 폴백한다.
     */
    public static NoticeResponse from(Notice notice, boolean english) {
        NoticeResponse response = from(notice);
        if (english) {
            if (notice.getTitleEn() != null && !notice.getTitleEn().isBlank()) {
                response.setTitle(notice.getTitleEn());
            }
            if (notice.getContentEn() != null && !notice.getContentEn().isBlank()) {
                response.setContent(notice.getContentEn());
            }
        }
        return response;
    }
```

`NoticeResponse`에 setter가 없다면 클래스에 `@Setter`를 추가하거나,
빌더를 사용 중이라면 빌더로 새 인스턴스를 만드는 방식으로 바꾼다.
기존 클래스의 Lombok 애노테이션을 먼저 확인할 것.

- [ ] **Step 9: 테스트가 통과하는지 확인한다**

```bash
./gradlew test --tests "com.danzzan.domain.notice.NoticeResponseLangTest"
```

Expected: PASS (3개 테스트)

- [ ] **Step 10: 컨트롤러에 lang 파라미터를 추가한다**

`src/main/java/com/danzzan/domain/notice/controller/NoticeController.java`의 목록/단건 조회 메서드에
파라미터를 추가하고 서비스로 전달한다:

```java
    @RequestParam(name = "lang", required = false, defaultValue = "ko") String lang
```

서비스 호출부에서 `"en".equalsIgnoreCase(lang)` 결과를 `NoticeResponse.from(notice, english)`로 넘긴다.

- [ ] **Step 11: 로컬에서 확인한다**

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

다른 터미널에서 공지를 하나 등록한 뒤:

```bash
curl -s "http://127.0.0.1:8080/notices?lang=en" | python3 -m json.tool
```

Expected: `title`, `content`가 영문으로 반환된다.

- [ ] **Step 12: 커밋한다**

```bash
git add src/main/java/com/danzzan/domain/notice/dto/response/NoticeResponse.java \
        src/main/java/com/danzzan/domain/notice/controller/NoticeController.java \
        src/test/java/com/danzzan/domain/notice/NoticeResponseLangTest.java
git commit -m "feat: 공지 조회에 lang 파라미터와 영문 폴백 추가"
```

---

### Task 7: 나머지 다섯 엔티티에 동일 패턴 적용

**Files:**
- Modify: `src/main/java/com/danzzan/domain/boothmap/model/entity/Booth.java`
- Modify: `src/main/java/com/danzzan/domain/boothmap/model/entity/Pub.java`
- Modify: `src/main/java/com/danzzan/domain/boothmap/model/entity/College.java`
- Modify: `src/main/java/com/danzzan/domain/timetable/model/entity/Artist.java`
- Modify: `src/main/java/com/danzzan/domain/timetable/model/entity/Performance.java`
- Modify: 각 엔티티의 서비스·응답 DTO·컨트롤러
- Test: `src/test/java/com/danzzan/domain/boothmap/BoothmapTranslationTest.java`, `src/test/java/com/danzzan/domain/timetable/TimetableTranslationTest.java`

**Interfaces:**
- Consumes: Task 5·6에서 확립한 패턴 (`applyTranslation`, `applyManualTranslation`, `from(entity, boolean english)`)
- Produces: 각 엔티티의 동일한 메서드 집합

Task 5·6의 `Notice` 구현이 참조 구현이다. 아래 표의 필드로 같은 구조를 반복한다.
필드 개수만 다르고 메서드 형태는 동일하다.

| 엔티티 | 번역 필드 | 컬럼 |
|---|---|---|
| `Booth` | name, description | `name_en`, `description_en` |
| `Pub` | name, intro, description, department | `name_en`, `intro_en`, `description_en`, `department_en` |
| `College` | name | `name_en` |
| `Artist` | name, description | `name_en`, `description_en` |
| `Performance` | stage | `stage_en` |

모든 엔티티에 `en_is_manual BOOLEAN NOT NULL DEFAULT FALSE`가 공통으로 들어간다.

- [ ] **Step 1: Booth 엔티티에 필드와 메서드를 추가한다**

`src/main/java/com/danzzan/domain/boothmap/model/entity/Booth.java`의 `createdAt` 필드 선언 앞에 추가한다:

```java
    @Column(name = "name_en")
    private String nameEn;

    @Column(name = "description_en")
    private String descriptionEn;

    @Column(name = "en_is_manual", nullable = false)
    private boolean enIsManual = false;
```

클래스 끝에 추가한다:

```java
    public void applyTranslation(String nameEn, String descriptionEn) {
        if (this.enIsManual) {
            return;
        }
        if (nameEn != null) {
            this.nameEn = nameEn;
        }
        if (descriptionEn != null) {
            this.descriptionEn = descriptionEn;
        }
    }

    public void applyManualTranslation(String nameEn, String descriptionEn) {
        this.nameEn = nameEn;
        this.descriptionEn = descriptionEn;
        this.enIsManual = true;
    }
```

- [ ] **Step 2: Pub 엔티티에 필드와 메서드를 추가한다**

`src/main/java/com/danzzan/domain/boothmap/model/entity/Pub.java`의 `createdAt` 필드 선언 앞에 추가한다:

```java
    @Column(name = "name_en")
    private String nameEn;

    @Column(name = "intro_en")
    private String introEn;

    @Column(name = "description_en", columnDefinition = "TEXT")
    private String descriptionEn;

    @Column(name = "department_en")
    private String departmentEn;

    @Column(name = "en_is_manual", nullable = false)
    private boolean enIsManual = false;
```

클래스 끝에 추가한다:

```java
    public void applyTranslation(String nameEn, String introEn,
                                 String descriptionEn, String departmentEn) {
        if (this.enIsManual) {
            return;
        }
        if (nameEn != null) {
            this.nameEn = nameEn;
        }
        if (introEn != null) {
            this.introEn = introEn;
        }
        if (descriptionEn != null) {
            this.descriptionEn = descriptionEn;
        }
        if (departmentEn != null) {
            this.departmentEn = departmentEn;
        }
    }

    public void applyManualTranslation(String nameEn, String introEn,
                                       String descriptionEn, String departmentEn) {
        this.nameEn = nameEn;
        this.introEn = introEn;
        this.descriptionEn = descriptionEn;
        this.departmentEn = departmentEn;
        this.enIsManual = true;
    }
```

- [ ] **Step 3: College, Artist, Performance 엔티티에 동일 패턴을 적용한다**

`College` (필드 1개):

```java
    @Column(name = "name_en")
    private String nameEn;

    @Column(name = "en_is_manual", nullable = false)
    private boolean enIsManual = false;

    public void applyTranslation(String nameEn) {
        if (this.enIsManual) {
            return;
        }
        if (nameEn != null) {
            this.nameEn = nameEn;
        }
    }

    public void applyManualTranslation(String nameEn) {
        this.nameEn = nameEn;
        this.enIsManual = true;
    }
```

`Artist` (필드 2개) — `Booth`와 동일한 형태이므로 `name`/`description`을 그대로 사용한다:

```java
    @Column(name = "name_en")
    private String nameEn;

    @Column(name = "description_en")
    private String descriptionEn;

    @Column(name = "en_is_manual", nullable = false)
    private boolean enIsManual = false;

    public void applyTranslation(String nameEn, String descriptionEn) {
        if (this.enIsManual) {
            return;
        }
        if (nameEn != null) {
            this.nameEn = nameEn;
        }
        if (descriptionEn != null) {
            this.descriptionEn = descriptionEn;
        }
    }

    public void applyManualTranslation(String nameEn, String descriptionEn) {
        this.nameEn = nameEn;
        this.descriptionEn = descriptionEn;
        this.enIsManual = true;
    }
```

`Performance` (필드 1개):

```java
    @Column(name = "stage_en")
    private String stageEn;

    @Column(name = "en_is_manual", nullable = false)
    private boolean enIsManual = false;

    public void applyTranslation(String stageEn) {
        if (this.enIsManual) {
            return;
        }
        if (stageEn != null) {
            this.stageEn = stageEn;
        }
    }

    public void applyManualTranslation(String stageEn) {
        this.stageEn = stageEn;
        this.enIsManual = true;
    }
```

- [ ] **Step 4: 부스맵 번역 테스트를 작성한다**

`src/test/java/com/danzzan/domain/boothmap/BoothmapTranslationTest.java`:

```java
package com.danzzan.domain.boothmap;

import com.danzzan.domain.boothmap.model.entity.Pub;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoothmapTranslationTest {

    @Test
    void 주점_자동번역이_네_필드를_모두_채운다() {
        Pub pub = new Pub();

        pub.applyTranslation("Engineering Pub", "Come join us",
                "Student-run pub", "Mechanical Engineering");

        assertEquals("Engineering Pub", pub.getNameEn());
        assertEquals("Come join us", pub.getIntroEn());
        assertEquals("Student-run pub", pub.getDescriptionEn());
        assertEquals("Mechanical Engineering", pub.getDepartmentEn());
    }

    @Test
    void 수동번역_이후에는_자동번역이_덮어쓰지_않는다() {
        Pub pub = new Pub();
        pub.applyManualTranslation("Manual", "Manual", "Manual", "Manual");

        pub.applyTranslation("Machine", "Machine", "Machine", "Machine");

        assertEquals("Manual", pub.getNameEn());
        assertTrue(pub.isEnIsManual());
    }
}
```

`Pub`에 인자 없는 생성자가 없다면 기존 정적 팩토리 메서드를 사용한다.
클래스 상단의 Lombok 애노테이션과 생성 방식을 먼저 확인할 것.

- [ ] **Step 5: 테스트가 통과하는지 확인한다**

```bash
./gradlew test --tests "com.danzzan.domain.boothmap.BoothmapTranslationTest"
```

Expected: PASS (2개 테스트)

- [ ] **Step 6: 각 도메인 서비스에 번역 호출을 연결한다**

대상 클래스와 메서드는 다음과 같다. 추측하지 말고 이 목록을 그대로 쓴다.

| 클래스 | 메서드 | 번역할 필드 |
|---|---|---|
| `domain/admin/map/service/AdminBoothManagementService` | `createBoothManagement(CreateAdminBoothRequest)` | name, description |
| `domain/admin/map/service/AdminBoothManagementService` | `updateBoothManagement(Long, UpdateAdminBoothRequest)` | name, description |
| `domain/admin/map/service/AdminBoothManagementService` | `createPubManagement(CreateAdminPubRequest)` | name, intro, description, department |
| `domain/admin/map/service/AdminBoothManagementService` | `updatePubManagement(Long, UpdateAdminPubRequest)` | name, intro, description, department |
| `domain/timetable/service/AdminArtistService` | `createArtist(CreateArtistRequest)` | name, description |
| `domain/timetable/service/AdminArtistService` | `updateArtist(Integer, UpdateArtistRequest)` | name, description |
| `domain/timetable/service/AdminPerformanceService` | `createPerformance(CreatePerformanceRequest)` | stage |
| `domain/timetable/service/AdminPerformanceService` | `updatePerformance(Integer, UpdatePerformanceRequest)` | stage |

각 클래스에 필드를 추가한다:

```java
    private final TranslationService translationService;
```

```java
import com.danzzan.infra.translation.TranslationService;
import java.util.List;
```

생성 메서드에서는 저장 직전에 다음을 넣는다. 아래는 `createBoothManagement`의 예이며,
필드가 더 많은 `createPubManagement`는 `List.of(...)`와 `applyTranslation(...)`의
인자 개수만 늘린다:

```java
        List<String> translated = translationService.translateAll(
                List.of(
                        request.getName() == null ? "" : request.getName(),
                        request.getDescription() == null ? "" : request.getDescription()
                )
        );
        booth.applyTranslation(translated.get(0), translated.get(1));
```

수정 메서드에서는 한국어가 실제로 바뀐 경우에만 재번역한다.
setter 호출 **전에** 기존 값을 보관한다:

```java
        String previousName = booth.getName();
        String previousDescription = booth.getDescription();
```

setter 호출이 모두 끝난 뒤:

```java
        boolean koreanChanged =
                !java.util.Objects.equals(previousName, request.getName())
                        || !java.util.Objects.equals(previousDescription, request.getDescription());

        if (koreanChanged) {
            List<String> retranslated = translationService.translateAll(
                    List.of(
                            request.getName() == null ? "" : request.getName(),
                            request.getDescription() == null ? "" : request.getDescription()
                    )
            );
            booth.applyTranslation(retranslated.get(0), retranslated.get(1));
        }
```

`College`는 관리자 화면에서 이름을 만들지 않고 위치만 수정한다
(`AdminMapController#updateCollegeLocation`). 따라서 생성 시점 번역 연결 대상이 아니며,
Task 10의 일회성 보정으로만 채운다.

- [ ] **Step 7: 각 응답 DTO에 `from(entity, boolean english)` 오버로드를 추가한다**

`NoticeResponse`와 동일한 형태다. 영문이 비어 있으면 한국어로 폴백한다.
`Booth`용 예시이며, 다른 DTO는 필드명만 바꾼다:

```java
    /**
     * 언어에 따라 값을 골라 담는다. 필드명은 바뀌지 않는다.
     * 영문이 비어 있으면 한국어로 폴백한다.
     */
    public static BoothResponse from(Booth booth, boolean english) {
        BoothResponse response = from(booth);
        if (english) {
            if (booth.getNameEn() != null && !booth.getNameEn().isBlank()) {
                response.setName(booth.getNameEn());
            }
            if (booth.getDescriptionEn() != null && !booth.getDescriptionEn().isBlank()) {
                response.setDescription(booth.getDescriptionEn());
            }
        }
        return response;
    }
```

기존 `from(entity)`는 지우지 않는다. 관리자 경로가 계속 사용한다.

- [ ] **Step 8: 각 조회 컨트롤러에 lang 파라미터를 추가한다**

각 조회 메서드 시그니처에 추가한다:

```java
    @RequestParam(name = "lang", required = false, defaultValue = "ko") String lang
```

그리고 응답 매핑부에서 `"en".equalsIgnoreCase(lang)` 결과를 `from(entity, english)`로 넘긴다.

대상 엔드포인트:

| 엔드포인트 | 컨트롤러 |
|---|---|
| `GET /booths/**` | `domain/boothmap/controller` 아래 부스 조회 컨트롤러 |
| `GET /map/**` | `domain/boothmap/controller` 아래 지도 조회 컨트롤러 |
| `GET /timetable/**` | `domain/timetable/controller` 아래 타임테이블 조회 컨트롤러 |

정확한 파일명은 다음으로 확인한다:

```bash
grep -rln "@GetMapping" src/main/java/com/danzzan/domain/boothmap/controller src/main/java/com/danzzan/domain/timetable/controller
```

**`/admin`으로 시작하는 컨트롤러에는 `lang` 파라미터를 넣지 않는다.**
관리자는 항상 한국어와 영어를 모두 받는다 (Task 9).

- [ ] **Step 9: 전체 테스트를 실행한다**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 10: 커밋한다**

```bash
git add src/main/java/com/danzzan/domain/boothmap/ \
        src/main/java/com/danzzan/domain/timetable/ \
        src/test/java/com/danzzan/domain/boothmap/BoothmapTranslationTest.java
git commit -m "feat: 부스·주점·단과대·아티스트·공연 영문 컬럼 및 lang 파라미터 추가"
```

---

### Task 8: 누락 번역 보정 스케줄러

**Files:**
- Create: `src/main/java/com/danzzan/domain/notice/service/NoticeTranslationBackfillService.java`
- Modify: `src/main/java/com/danzzan/domain/notice/repository/NoticeRepository.java`
- Test: `src/test/java/com/danzzan/domain/notice/NoticeTranslationBackfillServiceTest.java`

**Interfaces:**
- Consumes: `TranslationService#translateAll(List<String>)` (Task 4), `Notice#applyTranslation(String, String)` (Task 5)
- Produces: `NoticeTranslationBackfillService#backfill(): int` — 이번 실행에서 채운 공지 수를 반환한다

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`src/test/java/com/danzzan/domain/notice/NoticeTranslationBackfillServiceTest.java`:

```java
package com.danzzan.domain.notice;

import com.danzzan.domain.notice.entity.Notice;
import com.danzzan.domain.notice.repository.NoticeRepository;
import com.danzzan.domain.notice.service.NoticeTranslationBackfillService;
import com.danzzan.infra.translation.TranslationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoticeTranslationBackfillServiceTest {

    @Mock
    private NoticeRepository noticeRepository;

    @Mock
    private TranslationService translationService;

    @InjectMocks
    private NoticeTranslationBackfillService backfillService;

    private Notice untranslatedNotice() {
        return Notice.create("제목", "내용", "작성자", "GENERAL",
                false, null, List.of(), false);
    }

    @Test
    void 영문이_비어있는_공지를_채운다() {
        when(noticeRepository.findTop50ByTitleEnIsNullOrContentEnIsNull())
                .thenReturn(List.of(untranslatedNotice()));
        when(translationService.translateAll(any()))
                .thenReturn(List.of("Title", "Content"));

        assertEquals(1, backfillService.backfill());
    }

    @Test
    void 채울_공지가_없으면_번역을_호출하지_않는다() {
        when(noticeRepository.findTop50ByTitleEnIsNullOrContentEnIsNull())
                .thenReturn(List.of());

        assertEquals(0, backfillService.backfill());
        verify(translationService, never()).translateAll(any());
    }

    @Test
    void 번역이_또_실패하면_영문을_비운_채로_남긴다() {
        Notice notice = untranslatedNotice();
        when(noticeRepository.findTop50ByTitleEnIsNullOrContentEnIsNull())
                .thenReturn(List.of(notice));
        when(translationService.translateAll(any()))
                .thenReturn(Arrays.asList(null, null));

        backfillService.backfill();

        assertNull(notice.getTitleEn());
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인한다**

```bash
./gradlew test --tests "com.danzzan.domain.notice.NoticeTranslationBackfillServiceTest"
```

Expected: 컴파일 실패. `cannot find symbol: class NoticeTranslationBackfillService`

- [ ] **Step 3: 리포지토리에 조회 메서드를 추가한다**

`src/main/java/com/danzzan/domain/notice/repository/NoticeRepository.java`에 추가한다:

```java
    List<Notice> findTop50ByTitleEnIsNullOrContentEnIsNull();
```

`java.util.List` import가 없으면 추가한다.

한 번에 50건으로 제한하는 이유는 DeepL 호출량과 트랜잭션 길이를 묶어두기 위해서다.

- [ ] **Step 4: 스케줄러를 구현한다**

`src/main/java/com/danzzan/domain/notice/service/NoticeTranslationBackfillService.java`:

```java
package com.danzzan.domain.notice.service;

import com.danzzan.domain.notice.entity.Notice;
import com.danzzan.domain.notice.repository.NoticeRepository;
import com.danzzan.infra.translation.TranslationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeTranslationBackfillService {

    private final NoticeRepository noticeRepository;
    private final TranslationService translationService;

    /**
     * 영문이 비어 있는 공지를 주기적으로 채운다.
     * 이미 값이 있는 필드는 건드리지 않으므로 사람이 쓴 번역이 덮어써지지 않는다.
     */
    @Scheduled(fixedDelayString = "${translation.backfill.fixed-delay-ms:300000}")
    @Transactional
    public int backfill() {
        List<Notice> targets = noticeRepository.findTop50ByTitleEnIsNullOrContentEnIsNull();
        if (targets.isEmpty()) {
            return 0;
        }

        int filled = 0;
        for (Notice notice : targets) {
            List<String> translated = translationService.translateAll(
                    List.of(
                            notice.getTitleEn() == null ? safe(notice.getTitle()) : "",
                            notice.getContentEn() == null ? safe(notice.getContent()) : ""
                    )
            );
            notice.applyTranslation(translated.get(0), translated.get(1));
            if (notice.getTitleEn() != null || notice.getContentEn() != null) {
                filled++;
            }
        }

        log.info("공지 번역 보정 완료. 대상={}, 채움={}", targets.size(), filled);
        return filled;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
```

이미 채워진 필드에 빈 문자열을 넘기는 것이 핵심이다.
`TranslationService`가 빈 문자열을 건너뛰므로 DeepL 호출량이 줄고 기존 값도 보존된다.

- [ ] **Step 5: 스케줄링을 활성화한다**

`src/main/java/com/danzzan/` 아래에서 `@EnableScheduling`이 이미 있는지 확인한다:

```bash
grep -rn "@EnableScheduling" src/main/java
```

없으면 메인 애플리케이션 클래스에 추가한다. 이미 있으면 이 단계를 건너뛴다.

- [ ] **Step 6: 테스트가 통과하는지 확인한다**

```bash
./gradlew test --tests "com.danzzan.domain.notice.NoticeTranslationBackfillServiceTest"
```

Expected: PASS (3개 테스트)

- [ ] **Step 7: 커밋한다**

```bash
git add src/main/java/com/danzzan/domain/notice/service/NoticeTranslationBackfillService.java \
        src/main/java/com/danzzan/domain/notice/repository/NoticeRepository.java \
        src/test/java/com/danzzan/domain/notice/NoticeTranslationBackfillServiceTest.java
git commit -m "feat: 영문 누락 공지 보정 스케줄러 추가"
```

- [ ] **Step 8: 나머지 엔티티의 리포지토리에 조회 메서드를 추가한다**

`Notice`만 보정하면 부스·주점·아티스트의 번역 실패가 영구히 남는다.
각 리포지토리에 다음을 추가한다:

```java
    // BoothRepository
    List<Booth> findTop50ByNameEnIsNullOrDescriptionEnIsNull();

    // PubRepository
    List<Pub> findTop50ByNameEnIsNullOrIntroEnIsNullOrDescriptionEnIsNullOrDepartmentEnIsNull();

    // CollegeRepository
    List<College> findTop50ByNameEnIsNull();

    // ArtistRepository
    List<Artist> findTop50ByNameEnIsNullOrDescriptionEnIsNull();

    // PerformanceRepository
    List<Performance> findTop50ByStageEnIsNull();
```

각 파일에 `java.util.List` import가 없으면 추가한다.

- [ ] **Step 9: 통합 보정 서비스를 작성한다**

`src/main/java/com/danzzan/domain/admin/service/TranslationBackfillService.java`:

```java
package com.danzzan.domain.admin.service;

import com.danzzan.domain.boothmap.repository.BoothRepository;
import com.danzzan.domain.boothmap.repository.CollegeRepository;
import com.danzzan.domain.boothmap.repository.PubRepository;
import com.danzzan.domain.notice.service.NoticeTranslationBackfillService;
import com.danzzan.domain.timetable.repository.ArtistRepository;
import com.danzzan.domain.timetable.repository.PerformanceRepository;
import com.danzzan.infra.translation.TranslationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationBackfillService {

    private final NoticeTranslationBackfillService noticeBackfillService;
    private final BoothRepository boothRepository;
    private final PubRepository pubRepository;
    private final CollegeRepository collegeRepository;
    private final ArtistRepository artistRepository;
    private final PerformanceRepository performanceRepository;
    private final TranslationService translationService;

    @Scheduled(fixedDelayString = "${translation.backfill.fixed-delay-ms:300000}")
    @Transactional
    public int backfillAll() {
        int filled = noticeBackfillService.backfill();

        for (var booth : boothRepository.findTop50ByNameEnIsNullOrDescriptionEnIsNull()) {
            List<String> t = translationService.translateAll(
                    List.of(safe(booth.getNameEn(), booth.getName()),
                            safe(booth.getDescriptionEn(), booth.getDescription())));
            booth.applyTranslation(t.get(0), t.get(1));
            filled++;
        }

        for (var pub : pubRepository
                .findTop50ByNameEnIsNullOrIntroEnIsNullOrDescriptionEnIsNullOrDepartmentEnIsNull()) {
            List<String> t = translationService.translateAll(
                    List.of(safe(pub.getNameEn(), pub.getName()),
                            safe(pub.getIntroEn(), pub.getIntro()),
                            safe(pub.getDescriptionEn(), pub.getDescription()),
                            safe(pub.getDepartmentEn(), pub.getDepartment())));
            pub.applyTranslation(t.get(0), t.get(1), t.get(2), t.get(3));
            filled++;
        }

        for (var college : collegeRepository.findTop50ByNameEnIsNull()) {
            college.applyTranslation(translationService.translate(college.getName()));
            filled++;
        }

        for (var artist : artistRepository.findTop50ByNameEnIsNullOrDescriptionEnIsNull()) {
            List<String> t = translationService.translateAll(
                    List.of(safe(artist.getNameEn(), artist.getName()),
                            safe(artist.getDescriptionEn(), artist.getDescription())));
            artist.applyTranslation(t.get(0), t.get(1));
            filled++;
        }

        for (var performance : performanceRepository.findTop50ByStageEnIsNull()) {
            performance.applyTranslation(translationService.translate(performance.getStage()));
            filled++;
        }

        log.info("전체 번역 보정 완료. 처리={}", filled);
        return filled;
    }

    /**
     * 이미 영문이 채워진 필드는 빈 문자열을 넘겨 DeepL 호출에서 제외한다.
     */
    private String safe(String existingEnglish, String korean) {
        if (existingEnglish != null) {
            return "";
        }
        return korean == null ? "" : korean;
    }
}
```

`NoticeTranslationBackfillService#backfill`의 `@Scheduled` 애노테이션은 제거한다.
스케줄 진입점을 하나로 모아야 두 번 도는 것을 막을 수 있다.

리포지토리 패키지 경로는 실제와 다를 수 있다. 다음으로 확인한다:

```bash
find src/main/java -name "BoothRepository.java" -o -name "PubRepository.java" \
     -o -name "CollegeRepository.java" -o -name "ArtistRepository.java"
```

- [ ] **Step 10: 전체 테스트를 실행한다**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 11: 커밋한다**

```bash
git add src/main/java/com/danzzan/domain/admin/service/TranslationBackfillService.java \
        src/main/java/com/danzzan/domain/boothmap/repository/ \
        src/main/java/com/danzzan/domain/timetable/repository/ \
        src/main/java/com/danzzan/domain/notice/service/NoticeTranslationBackfillService.java
git commit -m "feat: 전체 엔티티 번역 보정 스케줄러로 통합"
```

---

### Task 9: 관리자 API에 영문 노출과 수동 입력

**Files:**
- Modify: `src/main/java/com/danzzan/domain/notice/dto/request/CreateNoticeRequest.java`
- Modify: `src/main/java/com/danzzan/domain/notice/dto/request/UpdateNoticeRequest.java`
- Modify: `src/main/java/com/danzzan/domain/notice/dto/response/NoticeResponse.java`
- Modify: `src/main/java/com/danzzan/domain/notice/service/NoticeService.java`
- Test: `src/test/java/com/danzzan/domain/notice/AdminNoticeTranslationTest.java`

**Interfaces:**
- Consumes: `Notice#applyManualTranslation(String, String)` (Task 5)
- Produces: `CreateNoticeRequest#getTitleEn()`, `CreateNoticeRequest#getContentEn()`, `NoticeResponse#getTitleEn()`, `NoticeResponse#getContentEn()`, `NoticeResponse#isEnIsManual()`

**사용자 API와 반대 규약이다.** 관리자는 한국어와 영어를 동시에 다뤄야 하므로 두 필드를 모두 노출한다.

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`src/test/java/com/danzzan/domain/notice/AdminNoticeTranslationTest.java`:

```java
package com.danzzan.domain.notice;

import com.danzzan.domain.notice.dto.request.CreateNoticeRequest;
import com.danzzan.domain.notice.entity.Notice;
import com.danzzan.domain.notice.repository.NoticeRepository;
import com.danzzan.domain.notice.service.NoticeService;
import com.danzzan.infra.translation.TranslationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminNoticeTranslationTest {

    @Mock
    private NoticeRepository noticeRepository;

    @Mock
    private TranslationService translationService;

    @InjectMocks
    private NoticeService noticeService;

    private CreateNoticeRequest requestWithEnglish(String titleEn, String contentEn) {
        CreateNoticeRequest request = new CreateNoticeRequest();
        request.setTitle("우천 안내");
        request.setContent("야외 부스가 중단될 수 있습니다.");
        request.setAuthor("총학생회");
        request.setCategory("GENERAL");
        request.setIsPinned(false);
        request.setImages(List.of());
        request.setTitleEn(titleEn);
        request.setContentEn(contentEn);
        return request;
    }

    @Test
    void 관리자가_영문을_입력하면_자동번역을_호출하지_않는다() {
        when(noticeRepository.save(any(Notice.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        noticeService.create(requestWithEnglish("Rain notice", "Outdoor booths may close."));

        verify(translationService, never()).translateAll(any());

        ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
        verify(noticeRepository).save(captor.capture());
        assertEquals("Rain notice", captor.getValue().getTitleEn());
        assertTrue(captor.getValue().isEnIsManual());
    }

    @Test
    void 영문을_비워두면_자동번역을_호출한다() {
        when(translationService.translateAll(any()))
                .thenReturn(List.of("Auto title", "Auto content"));
        when(noticeRepository.save(any(Notice.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        noticeService.create(requestWithEnglish(null, null));

        verify(translationService).translateAll(any());

        ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
        verify(noticeRepository).save(captor.capture());
        assertEquals("Auto title", captor.getValue().getTitleEn());
        assertEquals(false, captor.getValue().isEnIsManual());
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인한다**

```bash
./gradlew test --tests "com.danzzan.domain.notice.AdminNoticeTranslationTest"
```

Expected: 컴파일 실패. `cannot find symbol: method setTitleEn(String)`

- [ ] **Step 3: 요청 DTO에 영문 필드를 추가한다**

`CreateNoticeRequest.java`와 `UpdateNoticeRequest.java` 양쪽에 추가한다:

```java
    /**
     * 관리자가 직접 입력한 영문 제목. 비어 있으면 자동 번역한다.
     */
    private String titleEn;

    /**
     * 관리자가 직접 입력한 영문 본문. 비어 있으면 자동 번역한다.
     */
    private String contentEn;
```

- [ ] **Step 4: 응답 DTO에 영문 필드를 추가한다**

`NoticeResponse.java`에 필드를 추가하고 `from(Notice)`에서 채운다:

```java
    private String titleEn;
    private String contentEn;
    private boolean enIsManual;
```

`from(Notice notice)` 내부에서 `notice.getTitleEn()`, `notice.getContentEn()`,
`notice.isEnIsManual()`을 매핑한다.

`from(Notice, boolean english)`는 Task 6에서 만든 대로 `title`/`content`만 바꾸므로
관리자용 필드는 그대로 유지된다.

- [ ] **Step 5: NoticeService.create에 수동 입력 분기를 추가한다**

Task 6 Step 3에서 작성한 `create` 메서드의 번역 블록을 다음으로 교체한다:

```java
        boolean hasManualEnglish =
                (request.getTitleEn() != null && !request.getTitleEn().isBlank())
                        || (request.getContentEn() != null && !request.getContentEn().isBlank());

        if (hasManualEnglish) {
            notice.applyManualTranslation(request.getTitleEn(), request.getContentEn());
        } else {
            List<String> translated = translationService.translateAll(
                    List.of(
                            request.getTitle() == null ? "" : request.getTitle(),
                            request.getContent() == null ? "" : request.getContent()
                    )
            );
            notice.applyTranslation(translated.get(0), translated.get(1));
        }
```

`update` 메서드에도 동일한 분기를 적용한다.

- [ ] **Step 6: 테스트가 통과하는지 확인한다**

```bash
./gradlew test --tests "com.danzzan.domain.notice.AdminNoticeTranslationTest"
```

Expected: PASS (2개 테스트)

- [ ] **Step 7: 나머지 엔티티의 관리자 DTO에 동일 패턴을 적용한다**

Task 7의 표에 따라 각 엔티티의 생성/수정 요청 DTO와 응답 DTO에
`*_en` 필드와 `enIsManual`을 추가하고, 서비스에 수동 입력 분기를 넣는다.

- [ ] **Step 8: 전체 테스트를 실행한다**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 9: 커밋한다**

```bash
git add src/main/java/com/danzzan/domain/ \
        src/test/java/com/danzzan/domain/notice/AdminNoticeTranslationTest.java
git commit -m "feat: 관리자 API에 영문 필드 노출 및 수동 입력 우선 처리 추가"
```

---

### Task 10: 기존 데이터 일회성 번역

**Files:**
- Create: `src/main/java/com/danzzan/domain/admin/controller/AdminTranslationBackfillController.java`
- Test: `src/test/java/com/danzzan/domain/admin/AdminTranslationBackfillControllerTest.java`

**Interfaces:**
- Consumes: `TranslationBackfillService#backfillAll(): int` (Task 8 Step 9)
- Produces: `POST /api/admin/translation/backfill` → `{"filled": <int>}`

스케줄러가 5분마다 돌지만, 마이그레이션 직후에는 수동으로 즉시 실행할 수단이 필요하다.

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`src/test/java/com/danzzan/domain/admin/AdminTranslationBackfillControllerTest.java`:

```java
package com.danzzan.domain.admin;

import com.danzzan.domain.admin.controller.AdminTranslationBackfillController;
import com.danzzan.domain.admin.service.TranslationBackfillService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminTranslationBackfillControllerTest {

    @Mock
    private TranslationBackfillService backfillService;

    @InjectMocks
    private AdminTranslationBackfillController controller;

    @Test
    void 보정_실행_결과_건수를_반환한다() {
        when(backfillService.backfillAll()).thenReturn(7);

        Map<String, Integer> response = controller.backfill();

        assertEquals(7, response.get("filled"));
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인한다**

```bash
./gradlew test --tests "com.danzzan.domain.admin.AdminTranslationBackfillControllerTest"
```

Expected: 컴파일 실패. `cannot find symbol: class AdminTranslationBackfillController`

- [ ] **Step 3: 컨트롤러를 구현한다**

`src/main/java/com/danzzan/domain/admin/controller/AdminTranslationBackfillController.java`:

```java
package com.danzzan.domain.admin.controller;

import com.danzzan.domain.admin.service.TranslationBackfillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/translation")
@RequiredArgsConstructor
public class AdminTranslationBackfillController {

    private final TranslationBackfillService backfillService;

    @PostMapping("/backfill")
    public Map<String, Integer> backfill() {
        return Map.of("filled", backfillService.backfillAll());
    }
}
```

`/api/admin/**`는 `SecurityConfig`에서 이미 `hasRole("ADMIN")`으로 보호되어 있으므로
별도 보안 설정이 필요 없다.

- [ ] **Step 4: 테스트가 통과하는지 확인한다**

```bash
./gradlew test --tests "com.danzzan.domain.admin.AdminTranslationBackfillControllerTest"
```

Expected: PASS

- [ ] **Step 5: 전체 테스트를 실행한다**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 로컬에서 전체 흐름을 확인한다**

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

관리자로 로그인해 공지를 하나 등록한 뒤:

```bash
curl -s "http://127.0.0.1:8080/notices" | python3 -m json.tool
curl -s "http://127.0.0.1:8080/notices?lang=en" | python3 -m json.tool
```

Expected: 첫 번째는 한국어, 두 번째는 영문이 같은 필드명으로 반환된다.

- [ ] **Step 7: DeepL 사용량을 확인한다**

```bash
KEY=$(grep '^DEEPL_API_KEY=' .env | cut -d= -f2-)
curl -s "https://api-free.deepl.com/v2/usage" -H "Authorization: DeepL-Auth-Key $KEY"
```

Expected: `character_count`가 0보다 크고 `character_limit`인 1000000보다 훨씬 작다.

- [ ] **Step 8: 커밋한다**

```bash
git add src/main/java/com/danzzan/domain/admin/controller/AdminTranslationBackfillController.java \
        src/test/java/com/danzzan/domain/admin/AdminTranslationBackfillControllerTest.java
git commit -m "feat: 관리자 수동 번역 보정 엔드포인트 추가"
```

---

## FE 담당자에게 전달할 API 규약

이 계획의 산출물 중 FE가 의존하는 부분이다. FE 작업은 이 규약만 알면 BE 완성을 기다리지 않아도 된다.

**사용자 조회 API**

| 항목 | 값 |
|---|---|
| 파라미터 | `?lang=en` (생략 또는 `ko`면 한국어) |
| 적용 엔드포인트 | `GET /notices`, `GET /booths/**`, `GET /map/**`, `GET /timetable/**` |
| 응답 필드명 | **바뀌지 않는다.** `title`, `content`, `name` 등 그대로이며 값만 영문이 된다 |
| 영문 누락 시 | 한국어 값을 그대로 반환한다. 빈 문자열이나 null이 오지 않는다 |

FE는 언어에 따라 다른 필드를 읽는 분기를 만들 필요가 없다. 쿼리 파라미터만 붙이면 된다.

**관리자 API는 다르다.** `title`과 `title_en`을 모두 반환하며, 요청에도 두 필드를 모두 받는다.
