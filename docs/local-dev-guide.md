# Danzzan 백엔드 로컬 개발 가이드

## 1. 설정 파일 구조 이해

Spring Boot는 실행 시 `SPRING_PROFILES_ACTIVE` 값을 보고 어떤 설정 파일을 쓸지 결정합니다.

```
src/main/resources/
  application.yml        ← 항상 읽힘 (공통 설정)
  application-dev.yml    ← SPRING_PROFILES_ACTIVE=dev 일 때 추가로 읽힘
  application-prod.yml   ← SPRING_PROFILES_ACTIVE=prod 일 때 추가로 읽힘
```

공통 파일 위에 프로파일 파일이 덮어씌워지는 방식입니다.

---

### application.yml (공통)
모든 환경에서 공통으로 쓰는 설정입니다.
```
- JWT 설정
- CORS 설정
- 메일 설정
- Kafka producer/consumer 설정
- 티켓팅 로직 설정
- NHN Object Storage 설정
- Actuator/모니터링 설정
```

---

### application-dev.yml (로컬 개발용)
로컬 컴퓨터에 띄운 Docker 인프라에 연결합니다.
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/danzzan  # 내 컴퓨터의 MySQL
    username: danzzan
    password: change-me
  jpa:
    hibernate:
      ddl-auto: update   # 엔티티 바뀌면 테이블 자동 수정 (개발 편의)
  data:
    redis:
      host: localhost    # 내 컴퓨터의 Redis
  kafka:
    bootstrap-servers: localhost:9094  # 내 컴퓨터의 Kafka

jwt:
  secret: dev-secret-key-for-local-testing-only-...  # 로컬용 더미 JWT 키
```

---

### application-prod.yml (운영 서버용)
서버의 `.env` 파일에서 실제 값을 읽어옵니다. 코드에 실제 비밀번호가 없습니다.
```yaml
spring:
  datasource:
    url: ${DB_URL}        # .env 파일에서 읽음 → NHN RDS 주소
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate  # 스키마 검증만 함, 절대 자동 변경 안 함 (운영 안전)
  data:
    redis:
      host: ${REDIS_HOST:localhost}
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}  # NHN 내부망 Kafka
```

---

## 2. Docker 구조 이해

Docker 관련 파일이 2개 있습니다.

```
docker-compose.yml        ← 로컬 개발용
docker-compose.prod.yml   ← 운영 서버용 (GitHub Actions이 사용)
```

### docker-compose.yml (로컬 개발용)
로컬에서 필요한 인프라를 한 번에 띄웁니다.

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

> Spring Boot 앱 컨테이너까지 포함되어 있지만, **평소 개발 시에는 인프라만 Docker로 띄우고 Spring Boot는 직접 실행하는 게 훨씬 편합니다.** (코드 수정이 즉시 반영되기 때문)

### docker-compose.prod.yml (운영 서버용)
운영 서버에는 MySQL, Redis, Kafka가 NHN 인프라로 따로 존재합니다. Spring Boot 앱만 컨테이너로 실행합니다.

```
┌──────────────────────────────────┐
│      docker-compose.prod.yml     │
│                                  │
│  ┌────────────────────────────┐  │
│  │      Spring Boot 앱만       │  │
│  │  SPRING_PROFILES_ACTIVE=prod│  │
│  └────────────────────────────┘  │
│                                  │
│  DB/Redis/Kafka는 NHN 인프라 사용 │
└──────────────────────────────────┘
```

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

---

## 4. 개발하는 방법 (매일 사용)

### 방법 A — 인프라만 Docker + Spring Boot 직접 실행 ✅ 권장

코드 수정이 즉시 반영되고 IDE 디버깅이 가능해서 **일반적인 개발에 이 방법을 씁니다.**

**Step 1. 인프라 실행**
```bash
docker compose up -d mysql redis kafka kafka-init
```
처음 실행하면 이미지를 다운로드하고 MySQL DB, Redis, Kafka가 자동 생성됩니다.

**Step 2. application-local.yml 생성 (최초 1회)**

`src/main/resources/application-local.yml.example` 파일을 복사해서 `application-local.yml`로 이름 바꿉니다.
이 파일은 gitignore 되어 있어서 GitHub에 올라가지 않습니다.

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

내용은 기본적으로 AWS RDS 테스트 DB 연결 정보가 들어있습니다.

**Step 3. Spring Boot 실행**

터미널에서:
```bash
SPRING_PROFILES_ACTIVE=dev,local ./gradlew bootRun
```

IntelliJ에서:
```
Run → Edit Configurations
→ Environment variables 항목에 추가
   SPRING_PROFILES_ACTIVE=dev,local
→ 실행
```

> 프로파일 적용 순서: `application.yml` → `application-dev.yml` → `application-local.yml`
> `application-local.yml`의 DB 설정이 최종적으로 적용되어 AWS RDS 테스트 DB에 연결됩니다.

**Step 3. 개발 및 테스트**

서버가 `http://localhost:8080` 에서 실행됩니다.
코드 수정 후 Spring Boot만 재시작하면 반영됩니다. Docker 인프라는 그대로 둬도 됩니다.

**Step 4. 종료**
```bash
# Spring Boot: IntelliJ 중지 버튼 or Ctrl+C

# Docker 인프라 종료
docker compose down

# Docker 인프라 + 데이터까지 완전 삭제 (초기화)
docker compose down -v
```

---

### 방법 B — 전체 Docker 실행

배포 전에 실제 운영 환경과 비슷하게 전체 테스트할 때 씁니다.

```bash
docker compose up -d --build
```

> Spring Boot 코드가 바뀌면 `--build`를 붙여서 이미지를 다시 빌드해야 합니다.
> 빌드 시간이 걸려서 평소 개발엔 불편합니다.

---

## 5. 개발 → 배포 전체 흐름

```
로컬에서 개발 (application-dev.yml 사용)
          ↓
기능 완성 후 브랜치 push
          ↓
PR 생성 → main 머지
          ↓
GitHub Actions 자동 실행
  1. 테스트 (./gradlew test)
  2. Docker 이미지 빌드
  3. Docker Hub 업로드
  4. 운영 서버 SSH 접속
  5. 새 이미지로 컨테이너 재시작
          ↓
운영 서버에서 application-prod.yml 적용
서버 .env의 실제 DB/Kafka/Redis 연결
```

---

## 6. 자주 쓰는 명령어 모음

```bash
# 인프라만 시작
docker compose up -d mysql redis kafka kafka-init

# 인프라 상태 확인
docker compose ps

# MySQL 직접 접속
docker exec -it danzzan-mysql mysql -u danzzan -pchange-me danzzan

# 인프라 로그 확인
docker compose logs kafka

# 인프라 종료 (데이터 유지)
docker compose down

# 인프라 초기화 (데이터 삭제)
docker compose down -v
```

---

## 7. 환경별 설정 요약

| 항목 | 로컬 (dev) | 운영 서버 (prod) |
|---|---|---|
| DB | localhost:3306/danzzan | NHN RDS (외부 URL) |
| Redis | localhost:6379 | 서버 내부 localhost |
| Kafka | localhost:9094 | 서버 내부 192.168.0.88 |
| ddl-auto | update (자동 수정) | validate (검증만) |
| JWT Secret | 로컬 더미 키 | 서버 .env의 실제 키 |
| 프로파일 | `dev` | `prod` |
