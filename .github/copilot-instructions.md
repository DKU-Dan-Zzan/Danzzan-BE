# GitHub Copilot Instructions

## Language Rule
- **Always respond in Korean.**
- Regardless of the input language, always reply in Korean.

## Code Review Rule
- When reviewing code or proposing code changes, **a `suggestion` block must always be included.**
- Do not provide only explanations — **always include actual code changes using a `suggestion` block.**
- A `suggestion` block must contain **code only**.
- Any explanation must be written **outside** the `suggestion` block.

---

## 프로젝트 개요

**Danzzan-BE**는 단국대학교 축제("단짠") 운영을 위한 Spring Boot 백엔드 API 서버입니다.  
티켓팅(선착순 대기열), 공지사항, 분실물, 타임테이블, 홈 화면 콘텐츠, 관리자 기능을 제공합니다.

- **언어 / 버전**: Java 17
- **프레임워크**: Spring Boot 3.5.10
- **빌드 도구**: Gradle 8.14.4 (Wrapper 포함)
- **DB**: MySQL (운영), H2 in-memory (테스트)
- **캐시 / 세션**: Redis
- **인증**: JWT (JJWT 0.11.5) — 사용자/관리자 토큰 분리
- **API 문서**: SpringDoc OpenAPI 2.8.5 (`/swagger-ui.html`)
- **기타**: Spring Security, Spring Data JPA, Spring WebFlux, Lombok, JSoup

---

## 빌드 · 테스트 · 실행

```bash
# 빌드
./gradlew build

# 애플리케이션 실행
./gradlew bootRun

# 테스트 실행 (H2 + 로컬 Redis 필요)
./gradlew test

# E2E 테스트 (Docker 필요 — MySQL/Redis/Mailpit 컨테이너 자동 생성)
./scripts/password_reset_e2e_check.sh
```

> ⚠️ 테스트 실행 시 `localhost:6379` Redis가 필요합니다. CI 환경에서 Redis 없이 테스트하면 실패합니다.

---

## 패키지 레이아웃

```
src/main/java/com/danzzan/
├── domain/
│   ├── admin/         # 관리자 로그인 · 토큰 관리
│   ├── auth/          # DKU 인증 및 회원가입
│   ├── event/         # 축제 이벤트 CRUD
│   ├── home/          # 홈 화면 이미지/콘텐츠
│   ├── lostitem/      # 분실물 관리
│   ├── notice/        # 공지사항 (일반 + 긴급)
│   ├── ticket/        # 티켓팅 핵심 로직 (대기열, 슬롯, 입장)
│   │   ├── redis/     # Redis 키 패턴 (TicketRedisKeys)
│   │   ├── scheduler/ # 입장 처리 스케줄러
│   │   └── service/   # 대기열/슬롯/클레임 서비스
│   ├── timetable/     # 공연 타임테이블
│   └── user/
│       ├── passwordreset/ # 비밀번호 재설정 (Redis + 이메일)
│       └── validation/    # PasswordPolicy (8자 이상, 특수문자 1개 이상)
├── global/
│   ├── config/        # SecurityConfig, RedisConfig, SwaggerConfig
│   ├── exception/     # GlobalExceptionHandler, 공통 예외
│   ├── filter/        # JwtAuthenticationFilter (관리자용)
│   ├── jwt/           # JwtProvider (관리자), JwtTokenProvider (사용자/티켓)
│   └── model/         # ApiResponse, ApiError 등 공통 모델
└── infra/
    └── dku/           # DKU 외부 API 연동 (크롤링)
```

---

## 인증 · 보안

### JWT 이중 Provider 구조

| Provider | 대상 | 필터 클래스 |
|---|---|---|
| `JwtProvider` | 관리자 (`/auth/**`, `/api/admin/**`) | `global.filter.JwtAuthenticationFilter` |
| `JwtTokenProvider` | 일반 사용자 (`/user/**`, `/tickets/**`) | `global.jwt.JwtAuthenticationFilter` |

- 두 필터는 `SecurityConfig`에서 `UsernamePasswordAuthenticationFilter` 앞에 모두 등록됩니다.
- **토큰 버전 관리**: `User` 엔티티의 `tokenVersion` 필드. 비밀번호 재설정 시 `bumpTokenVersion()`으로 증가 → 기존 세션 일괄 무효화.
- **BCrypt**: 강도 10으로 설정.

### 공개 엔드포인트 (인증 불필요)

`/auth/**`, `/user/login`, `/user/reissue`, `/user/dku/**`, `/user/password/reset/**`,  
`/home/**`, `/notices/**`, `/timetable/**`, `/lost-items/**`, `/tickets/events`,  
`/tickets/request`, `/tickets/status`, `/tickets/redis/**`, Swagger UI

---

## 코드 컨벤션

### DTO

```java
// 요청 DTO: final 필드 + @RequiredArgsConstructor
@Getter
@RequiredArgsConstructor
public class RequestLoginDto {
    @NotBlank
    private final String studentId;
    @NotBlank
    private final String password;
}

// 응답 DTO: @Builder + @NoArgsConstructor + @AllArgsConstructor
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketStatusResponseDTO {
    private Long eventId;
    private String status;
}
```

### 엔티티

- `@Entity` + `@Getter` + `@NoArgsConstructor(access = PROTECTED)` 패턴 사용.
- `@Builder`는 정적 팩토리 메서드 또는 내부 빌더로 제공.
- 기본키: `@GeneratedValue(strategy = GenerationType.IDENTITY)`.

### 예외 처리

- 모든 커스텀 예외는 `RuntimeException` 상속.
- `GlobalExceptionHandler`가 `@RestControllerAdvice`로 전역 처리.
- **티켓팅 API 판별**: `isTicketingApi()` 메서드가 `/user/**`, `/tickets/**`, `/api/admin/events/**`, `/api/admin/ticket/**` 경로를 감지 → 별도 에러 포맷(`Map.of("error", ...)`) 반환.

### 서비스 레이어

- 인터페이스 + `Impl` 패턴 사용 (`FooService` → `FooServiceImpl`).
- `@Qualifier`가 있는 필드는 `@RequiredArgsConstructor`와 함께 Spring이 생성자 파라미터 이름으로 매칭함 (`lombok.config` 불필요).

### 비밀번호 정책

`PasswordPolicy.java`에 중앙화: 8자 이상, 특수문자 1개 이상.  
회원가입(`RequestSignupDto`)과 비밀번호 재설정(`RequestPasswordResetDto`) DTO 모두 동일 어노테이션 사용.

---

## Redis 사용 패턴

### 키 패턴

`TicketRedisKeys` 클래스가 모든 티켓 관련 Redis 키 생성:
- `ticket:{eventId}:queue` — 대기열
- `ticket:{eventId}:slot` — 슬롯 카운터
- `ticket:{eventId}:gate:{userId}` — 입장 상태
- `password-reset:{...}` — 비밀번호 재설정 상태

### Lua 스크립트

원자적 연산이 필요한 경우 `src/main/resources/redis/` 아래 Lua 스크립트 사용:
- `acquire_slot.lua` — 슬롯 획득 (동시성 제어)
- `claim_v2.lua` — 티켓 클레임 + 재고 감소 (원자적)
- `password_reset_consume.lua` — 검증 토큰 1회 소비 (원자적)

### 인메모리 캐싱

`UserInfoMemoryRepositoryImpl` (`ConcurrentHashMap` 기반)이 자주 조회되는 사용자 정보를 캐싱합니다.  
빈번한 사용자 조회 시 직접 DB 쿼리 대신 `UserInfoService`를 통해 캐시를 활용하세요.

---

## 도메인 관계

```
User 1:N UserTicket N:1 FestivalEvent
FestivalEvent 1:N Performance N:1 Artist
Performance 1:N ContentImage
AdminEntity (독립 — User와 별개 테이블)
```

---

## 테스트 전략

- **단위 테스트**: Mockito로 의존성 모킹, `ReflectionTestUtils`로 private 필드 주입.
- **통합 테스트**: `@SpringBootTest` + H2 in-memory DB.
- **E2E 테스트**: `scripts/password_reset_e2e_check.sh` — Docker로 MySQL/Redis/Mailpit 컨테이너 자동 구성.
- 테스트 설정 파일: `src/test/resources/application.properties`.

---

## 알려진 주의 사항

1. **Redis 없이 테스트 불가**: 테스트 설정에서 `localhost:6379` Redis를 필요로 합니다. 로컬에서 `docker run -d -p 6379:6379 redis` 또는 CI에서 Redis 서비스 컨테이너를 설정하세요.
2. **두 개의 JWT 필터 충돌 주의**: 새 엔드포인트 추가 시 어느 필터 체인이 적용될지 `SecurityConfig`에서 확인하세요.
3. **프로덕션 DDL**: 테스트는 `create-drop`, 운영은 별도 `application.yml`에서 `validate` 또는 마이그레이션 도구를 사용해야 합니다.
4. **DKU 크롤링**: `infra/dku/` 모듈은 외부 DKU 포털 크롤링에 의존합니다. 외부 서비스 변경 시 `DkuFailedCrawlingException` 처리 확인 필요.
5. **Swagger UI**: 개발 중 `/swagger-ui.html`에서 API 탐색 가능. Bearer 토큰 인증 지원.
