# Danzzan 백엔드 로컬 개발 가이드

## 1. 설정 파일 구조 이해

Spring Boot는 실행 시 `SPRING_PROFILES_ACTIVE` 값을 보고 어떤 설정 파일을 쓸지 결정합니다.

```
src/main/resources/
  application.yml        ← 항상 읽힘 (공통 설정) — 깃허브 공개 ✅
  application-dev.yml    ← 로컬 개발용 — gitignore, 팀원 간 직접 공유 🔒
  application-prod.yml   ← 운영 서버용 — 깃허브 공개 ✅
```

공통 파일 위에 프로파일 파일이 덮어씌워지는 방식입니다.

> `application.yml`과 `application-prod.yml`은 실제 비밀번호 없이 `${ENV_VAR}` 형식만 사용하므로 깃허브에 올려도 안전합니다.
> `application-dev.yml`은 AWS RDS 테스트 DB 비밀번호가 있어서 gitignore 처리되어 있습니다. 팀장에게 직접 받으세요.

---

### application.yml (공통) — 깃허브 공개
모든 환경에서 공통으로 쓰는 설정입니다. 실제 비밀번호 없음.
```
- 기본 프로파일: dev (SPRING_PROFILES_ACTIVE 미설정 시 자동 적용)
- JWT 설정
- CORS 설정
- 메일 설정
- Kafka producer/consumer 설정
- 티켓팅 로직 설정
- NHN Object Storage 설정
- Actuator/모니터링 설정
```

---

### application-dev.yml (로컬 개발용) — gitignore 🔒
AWS RDS 테스트 DB에 연결합니다. **팀장에게 직접 파일 받아서 `src/main/resources/`에 넣으세요.**
```yaml
spring:
  datasource:
    url: jdbc:mysql://danzzan.cjoo2uugo4tk.ap-northeast-2.rds.amazonaws.com:3306/festival_test
    username: admin
    password: (팀장에게 문의)
  jpa:
    hibernate:
      ddl-auto: update   # 엔티티 바뀌면 테이블 자동 수정
  data:
    redis:
      host: localhost
  kafka:
    bootstrap-servers: localhost:9094

jwt:
  secret: dev-secret-key-for-local-testing-only  # 로컬용 더미 키
```

---

### application-prod.yml (운영 서버용) — 깃허브 공개
서버의 `.env` 파일에서 실제 값을 읽습니다. 코드에 비밀번호 없음.
```yaml
spring:
  datasource:
    url: ${DB_URL}        # 서버 .env에서 읽음 → NHN RDS
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate  # 스키마 검증만, 자동 변경 없음 (운영 안전)
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      password: ${REDIS_PASSWORD:}
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
```

---

## 2. Docker 구조 이해

Docker 관련 파일이 2개 있습니다.

```
docker-compose.yml        ← 로컬 개발용 (MySQL, Redis, Kafka, Spring Boot 포함)
docker-compose.prod.yml   ← 운영 서버용 (Spring Boot만, GitHub Actions가 사용)
```

### docker-compose.yml (로컬 개발용)

```
┌─────────────────────────────────────┐
│         docker-compose.yml          │
│                                     │
│  ┌─────────┐  ┌───────┐  ┌───────┐ │
│  │  MySQL  │  │ Redis │  │ Kafka │ │
│  │  :3306  │  │ :6379 │  │ :9094 │ │
│  └─────────┘  └───────┘  └───────┘ │
│                                     │
│  + Spring Boot 앱 컨테이너도 포함    │
└─────────────────────────────────────┘
```

> **평소 개발 시에는 인프라(Redis, Kafka)만 Docker로 띄우고 Spring Boot는 직접 실행하는 게 훨씬 편합니다.** 코드 수정이 즉시 반영되고 IDE 디버깅이 가능하기 때문입니다.

> DB는 AWS RDS 테스트 DB를 사용하므로 로컬 MySQL Docker는 불필요합니다.

### docker-compose.prod.yml (운영 서버용)
운영 서버에서 Spring Boot 앱만 컨테이너로 실행합니다. DB/Redis/Kafka는 NHN 인프라를 사용합니다.

---

## 3. 로컬 개발 세팅 (최초 1회)

### 사전 준비
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) 설치
- JDK 17 설치

### 프로젝트 클론
```bash
git clone https://github.com/DKU-Dan-Zzan/Danzzan-BE.git
cd Danzzan-BE
```

### application-dev.yml 파일 받기
팀장에게 `application-dev.yml` 파일을 받아서 아래 경로에 넣습니다.
```
src/main/resources/application-dev.yml
```

---

## 4. 개발하는 방법 (매일 사용)

### 방법 A — Redis/Kafka만 Docker + Spring Boot 직접 실행 ✅ 권장

코드 수정이 즉시 반영되고 IDE 디버깅이 가능해서 **일반적인 개발에 이 방법을 씁니다.**
DB는 AWS RDS 테스트 DB를 사용하므로 MySQL Docker는 필요 없습니다.

**Step 1. Redis, Kafka 실행**
```bash
docker compose up -d redis kafka kafka-init
```

**Step 2. Spring Boot 실행**

터미널에서:
```bash
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

IntelliJ에서:
```
Run → Edit Configurations
→ Environment variables에 추가: SPRING_PROFILES_ACTIVE=dev
→ 실행
```

> `application-dev.yml`이 적용되어 AWS RDS 테스트 DB에 연결됩니다.

**Step 3. 개발 및 테스트**

서버가 `http://localhost:8080`에서 실행됩니다.
코드 수정 후 Spring Boot만 재시작하면 됩니다. Docker 인프라는 그대로 둬도 됩니다.

**Step 4. 종료**
```bash
# Spring Boot: IntelliJ 중지 버튼 or Ctrl+C

# Docker 인프라 종료
docker compose down
```

---

### 방법 B — 전체 Docker 실행

배포 환경과 동일하게 전체 테스트할 때 씁니다.

```bash
docker compose up -d --build
```

> 코드가 바뀌면 `--build`로 이미지를 다시 빌드해야 합니다.

---

## 5. 개발 → 배포 전체 흐름

```
로컬에서 개발 (application-dev.yml 사용, AWS RDS 테스트 DB)
          ↓
기능 완성 후 브랜치 push
          ↓
PR 생성 → main 머지
          ↓
GitHub Actions 자동 실행
  1. 테스트 (./gradlew test) — H2 인메모리 DB 사용, 외부 연결 불필요
  2. Docker 이미지 빌드
  3. Docker Hub 업로드
  4. 운영 서버 SSH 접속
  5. 새 이미지로 컨테이너 재시작
          ↓
운영 서버: SPRING_PROFILES_ACTIVE=prod → application-prod.yml 적용
서버 .env의 실제 NHN DB/Redis/Kafka 연결
```

---

## 6. 자주 쓰는 명령어 모음

```bash
# Redis, Kafka만 시작 (권장)
docker compose up -d redis kafka kafka-init

# 인프라 상태 확인
docker compose ps

# 인프라 종료
docker compose down
```

---

## 7. 환경별 설정 요약

| 항목 | 로컬 (dev) | CI 테스트 | 운영 서버 (prod) |
|---|---|---|---|
| DB | AWS RDS 테스트 DB | H2 인메모리 | NHN RDS |
| Redis | localhost:6379 | 불필요 (Mock) | 서버 내부 localhost |
| Kafka | localhost:9094 | 불필요 | 서버 내부망 |
| ddl-auto | update | create-drop | validate |
| JWT Secret | 로컬 더미 키 | 테스트 더미 키 | 서버 .env의 실제 키 |
| 프로파일 | `dev` | `dev` (기본값) | `prod` |

---

## 8. 파일별 깃허브 공개 여부

| 파일 | 깃허브 | 이유 |
|---|---|---|
| `application.yml` | ✅ 공개 | 실제 비밀번호 없음 |
| `application-dev.yml` | 🔒 gitignore | AWS RDS 비밀번호 있음 |
| `application-prod.yml` | ✅ 공개 | 환경변수 참조만, 비밀번호 없음 |
| `.env` (서버) | 🔒 gitignore | 실제 모든 비밀번호 보관 |
