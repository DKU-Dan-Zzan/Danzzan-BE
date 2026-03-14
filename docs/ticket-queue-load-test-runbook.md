# Ticket Queue Load Test Runbook

이 문서는 팀원이 같은 저장소를 `pull` 한 뒤, 로컬에서 같은 조건으로 대기열 부하 테스트를 재현할 수 있도록 정리한 실행 문서다.

대상 구조:
- `POST /tickets/{eventId}/queue/enter`
- `GET /tickets/{eventId}/queue/status`
- `POST /tickets/{eventId}/activate`
- `POST /tickets/{eventId}/reserve`

테스트 도구:
- `k6`
- 부하 테스트 스크립트: [`k6-test/ticket-queue-flow.js`](/Users/ziuuu/Documents/capstone_23/k6-test/ticket-queue-flow.js)

## 1. 전제 조건

로컬에 아래가 떠 있어야 한다.

- MySQL: `localhost:3306`
- Redis: `localhost:6379`
- 백엔드: `localhost:8080`

현재 백엔드 기준 설정:
- [`application.yml`](/Users/ziuuu/Documents/capstone_23/Danzzan-BE/src/main/resources/application.yml)
- DB: `festival_test`
- Redis: `localhost:6379`
- `app.ticketing.max-concurrent-slots=100`
- `app.ticketing.gate-ttl-seconds=300`
- `app.ticketing.active-ttl-seconds=600`

테스트 당시 사용한 도구:
- Java 17
- Gradle Wrapper
- `k6` 1.6.1
  - 경로 예시: `/opt/homebrew/Cellar/k6/1.6.1/bin/k6`

## 2. 테스트에 사용한 이벤트 조건

로컬 DB `festival_events` 기준으로 아래를 사용했다.

- `event_id=1`
- `ticketing_status=OPEN`
- `total_capacity=3000`

테스트 목표 시나리오:
- 동시 유입 사용자: `8000명`
- 총 티켓 수: `3000장`
- capacity 비교값: `100`, `200`, `300`

주의:
- `event_id=1`은 반드시 `OPEN` 이어야 한다.
- `ticket_queue_entries` 및 Redis queue 상태가 남아 있으면 재현성이 깨진다.

## 3. 사용한 스크립트

부하 테스트 관련 파일:
- [`k6-test/ticket-queue-flow.js`](/Users/ziuuu/Documents/capstone_23/k6-test/ticket-queue-flow.js)
- [`k6-test/seed_test_users.py`](/Users/ziuuu/Documents/capstone_23/k6-test/seed_test_users.py)
- [`k6-test/reset_event_queue.py`](/Users/ziuuu/Documents/capstone_23/k6-test/reset_event_queue.py)

각 역할:
- `seed_test_users.py`
  - `TEST0001 ~ TEST8000` 같은 테스트 유저를 MySQL `users` 테이블에 넣는다.
- `reset_event_queue.py`
  - 특정 이벤트의 Redis queue 상태를 비운다.
  - 같은 이벤트의 `ticket_queue_entries` projection도 같이 지운다.
  - 테스트 유저에 한해 `user_tickets`도 정리한다.
  - stock 을 원하는 값으로 다시 세팅한다.
- `ticket-queue-flow.js`
  - `enter-only` 또는 `full-flow` 시나리오를 `k6`로 실행한다.

## 4. 왜 reset 스크립트가 필요한가

이번 테스트 과정에서 실제로 걸린 문제:

- Redis queue만 지우고 다시 테스트하면 안 된다.
- `ticket_queue_entries`에 이전 테스트의 row가 남아 있으면 `(event_id, seq)` 유니크 제약과 충돌한다.
- 그러면 `queue/enter` 자체가 가짜로 실패한 것처럼 보인다.

그래서 테스트 전에는 반드시:
- Redis queue/ready/active/seq/dedup/claim 상태 삭제
- `ticket_queue_entries` event 단위 삭제
- 테스트 유저용 `user_tickets` 삭제
- stock 재설정

이 4개를 같이 해야 한다.

## 5. 테스트 유저 생성

### 5-1. 8000명 테스트 유저 생성

```bash
cd /Users/ziuuu/Documents/capstone_23

COUNT=8000 \
PASSWORD=test1234 \
STUDENT_ID_PREFIX=TEST \
python3 k6-test/seed_test_users.py
```

성공 예시:

```text
seeded_or_updated=8000
```

생성되는 사용자 패턴:
- `student_id=TEST0001`
- `student_id=TEST0002`
- ...
- `student_id=TEST8000`

비밀번호:
- `test1234`

## 6. JWT 토큰 준비 방식

### 6-1. 권장 방식

부하 테스트용으로는 로그인 API를 8000번 치지 않고, 오프라인으로 access token 을 생성했다.

이유:
- 로그인 API 자체가 부하 테스트의 대상이 아님
- 토큰 생성 시간이 너무 오래 걸림
- queue 성능 측정 전에 인증 레이어에서 시간을 소모하면 결과가 흐려짐

### 6-2. 우리가 사용한 방식

MySQL에서 테스트 유저 `id`, `student_id`, `role`, `token_version` 을 읽고,
현재 [`JwtTokenProvider.java`](/Users/ziuuu/Documents/capstone_23/Danzzan-BE/src/main/java/com/danzzan/global/jwt/JwtTokenProvider.java) 와 같은 클레임 구조로 HS256 JWT를 만들어 `tokens_load_8000.json` 을 생성했다.

현재 토큰 payload 기준:
- `sub`
- `studentId`
- `role`
- `tokenVersion`
- `iat`
- `exp`

서명 secret:
- [`application.yml`](/Users/ziuuu/Documents/capstone_23/Danzzan-BE/src/main/resources/application.yml) 의 `jwt.secret`

생성 파일:
- `k6-test/tokens_load_8000.json`

주의:
- 이 파일은 테스트 산출물이라 커밋 대상이 아니다.
- 루트 `.gitignore`에 `k6-test/tokens*.json` 으로 제외해뒀다.

## 7. 백엔드 실행

```bash
cd /Users/ziuuu/Documents/capstone_23/Danzzan-BE
./gradlew bootRun
```

기동 후 확인:

```bash
curl -i http://localhost:8080/tickets/events
```

또는 queue smoke:

```bash
curl -i -X POST http://localhost:8080/tickets/1/queue/enter \
  -H "Authorization: Bearer <access-token>"
```

## 8. 테스트 전 초기화

매번 부하 테스트 전에 반드시 실행:

```bash
cd /Users/ziuuu/Documents/capstone_23

EVENT_ID=1 \
STOCK=3000 \
LIMIT=8000 \
STUDENT_ID_PREFIX=TEST \
python3 k6-test/reset_event_queue.py
```

성공 예시:

```text
event=1 cleared_keys=32005 stock=3000 users=8000
```

이 명령이 하는 일:
- Redis `ticket:1:queue`
- Redis `ticket:1:ready`
- Redis `ticket:1:active`
- Redis `ticket:1:seq`
- Redis `ticket:1:closed-cleanup`
- 테스트 유저별 `quser/dedup/user/status` 키 삭제
- `ticket_queue_entries where event_id = 1` 삭제
- 테스트 유저에 한한 `user_tickets where event_id = 1` 삭제
- `ticket:1:stock = 3000` 재설정

## 9. 실제 실행 명령어

### 9-1. 1000명 enter-only

```bash
/opt/homebrew/Cellar/k6/1.6.1/bin/k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e EVENT_ID=1 \
  -e FLOW_MODE=enter-only \
  -e TARGET_VUS=1000 \
  -e ITERATIONS_PER_VU=1 \
  -e MAX_DURATION=10m \
  -e TOKENS_FILE=/Users/ziuuu/Documents/capstone_23/k6-test/tokens_load_8000.json \
  /Users/ziuuu/Documents/capstone_23/k6-test/ticket-queue-flow.js
```

### 9-2. 3000명 enter-only

```bash
/opt/homebrew/Cellar/k6/1.6.1/bin/k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e EVENT_ID=1 \
  -e FLOW_MODE=enter-only \
  -e TARGET_VUS=3000 \
  -e ITERATIONS_PER_VU=1 \
  -e MAX_DURATION=15m \
  -e TOKENS_FILE=/Users/ziuuu/Documents/capstone_23/k6-test/tokens_load_8000.json \
  /Users/ziuuu/Documents/capstone_23/k6-test/ticket-queue-flow.js
```

### 9-3. 8000명 enter-only

지금 구조에서는 `3000명`에서도 이미 timeout 이 크게 나므로, `8000명`은 튜닝 후 재시도 대상이다.

그래도 동일 명령 형식은 아래다.

```bash
/opt/homebrew/Cellar/k6/1.6.1/bin/k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e EVENT_ID=1 \
  -e FLOW_MODE=enter-only \
  -e TARGET_VUS=8000 \
  -e ITERATIONS_PER_VU=1 \
  -e MAX_DURATION=20m \
  -e TOKENS_FILE=/Users/ziuuu/Documents/capstone_23/k6-test/tokens_load_8000.json \
  /Users/ziuuu/Documents/capstone_23/k6-test/ticket-queue-flow.js
```

### 9-4. full-flow

`full-flow`는 `enter -> poll -> activate -> reserve` 전체를 본다.

```bash
/opt/homebrew/Cellar/k6/1.6.1/bin/k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e EVENT_ID=1 \
  -e FLOW_MODE=full-flow \
  -e TARGET_VUS=1000 \
  -e ITERATIONS_PER_VU=1 \
  -e POLL_INTERVAL_MS=1000 \
  -e POLL_TIMEOUT_MS=240000 \
  -e MAX_DURATION=15m \
  -e TOKENS_FILE=/Users/ziuuu/Documents/capstone_23/k6-test/tokens_load_8000.json \
  /Users/ziuuu/Documents/capstone_23/k6-test/ticket-queue-flow.js
```

## 10. 실제로 우리가 진행한 순서

이번에 진행한 순서는 아래다.

1. 테스트 유저 8000명 생성
2. 8000명용 JWT 생성
3. 백엔드 최신 코드로 `8080` 재기동
4. 단일 `queue/enter` smoke 테스트
5. `reset_event_queue.py` 로 event 1 상태 초기화
6. `1000명 enter-only` 실행
7. 결과 확인
8. 다시 reset
9. `3000명 enter-only` 실행
10. 결과 확인

중간에 실제로 수정한 것:
- Redis만 비우면 안 돼서 `reset_event_queue.py` 에 DB 정리까지 추가
- `ticket_queue_entries` event 단위 삭제를 넣어 `(event_id, seq)` 충돌 해결

## 11. 실제 측정 결과

### 11-1. 1000명 enter-only

결과:
- `http_req_failed = 0.10%`
- `999 / 1000` 성공
- `queue_enter p95 = 33.51s`
- `avg = 19.25s`

해석:
- 기능 정합성은 거의 유지
- 하지만 응답시간이 매우 느림

### 11-2. 3000명 enter-only

결과:
- `http_req_failed = 44.83%`
- `1655 / 3000` 성공
- `queue_enter p95 = 60s`
- 실패 대부분은 request timeout

해석:
- 로컬 단일 인스턴스 기준으로 `3000명 동시 유입`은 이미 포화
- `8000명`은 현재 상태로는 유의미한 성공률 기대가 어려움

## 12. 결과 해석 기준

### 성공으로 볼 수 있는 조건
- `http_req_failed` 낮음
- 5xx 거의 없음
- `queue_enter` 성공률 높음
- 중복 seq/역전 없음
- 서버 로그에 `ticket_queue_entries` 유니크 충돌 없음

### 현재 관찰된 병목
- `queue/enter` 응답시간 자체가 큼
- 로컬 단일 인스턴스 + `show-sql=true` 상태라 DB/로그 오버헤드 큼
- `3000명`부터는 timeout이 눈에 띄게 증가

## 13. 재현 시 주의사항

1. `reset_event_queue.py` 없이 바로 재실행하지 말 것
2. 이전 테스트의 `ticket_queue_entries` row가 남아 있으면 결과가 왜곡됨
3. `tokens_load_8000.json` 은 생성 산출물이라 커밋하지 말 것
4. `show-sql=true` 상태는 성능을 악화시킬 수 있음
5. 로컬 단일 인스턴스 결과와 운영 멀티 인스턴스 결과를 동일시하면 안 됨

## 14. 다음 단계

현재 문서 기준 다음 순서:

1. `queue/enter` 병목 튜닝
2. 로그/SQL 출력 줄이기
3. `1000 -> 3000` 재측정
4. 이후 `8000 enter-only`
5. 그 다음 `full-flow`

## 15. 관련 파일

- [`application.yml`](/Users/ziuuu/Documents/capstone_23/Danzzan-BE/src/main/resources/application.yml)
- [`k6-test/ticket-queue-flow.js`](/Users/ziuuu/Documents/capstone_23/k6-test/ticket-queue-flow.js)
- [`k6-test/seed_test_users.py`](/Users/ziuuu/Documents/capstone_23/k6-test/seed_test_users.py)
- [`k6-test/reset_event_queue.py`](/Users/ziuuu/Documents/capstone_23/k6-test/reset_event_queue.py)
- [`TicketAdmissionScheduler.java`](/Users/ziuuu/Documents/capstone_23/Danzzan-BE/src/main/java/com/danzzan/domain/ticket/scheduler/TicketAdmissionScheduler.java)
- [`QueueStateServiceImpl.java`](/Users/ziuuu/Documents/capstone_23/Danzzan-BE/src/main/java/com/danzzan/domain/ticket/service/QueueStateServiceImpl.java)
