# 티켓팅 성능 병목 이슈 정리 (2026-04-17)

## 1. 테스트 결과 요약
- 테스트: 로컬 300명 동시 티켓팅 (`full-flow`)
- 기능 관점:
  - `http_req_failed`: `0.00%` (정상)
  - `reserve_success_rate`: `100.00%` (정상)
- 성능 관점:
  - `queue_enter p95`: `2.42s` (목표 `<500ms` 실패)
  - `queue_status p95`: `1.65s` (목표 `<300ms` 실패)
  - `activate p95`: `1.41s` (목표 `<500ms` 실패)
  - `reserve p95`: `1.70s` (목표 `<700ms` 실패)
  - `request_status p95`: `959.57ms` (목표 `<700ms` 실패)

## 2. 문제 상황별 정리

### 2.1 `POST /tickets/{eventId}/queue/enter` 지연
- 증상: p95 `2.42s`로 가장 느린 구간 중 하나.
- 문제 상황:
  - 요청당 DB 조회가 연속으로 발생:
    - `hasTicket(userId, eventId)` (이미 예매 여부)
    - `getTicketingStatus(eventId)` (이벤트 상태)
  - 동시 300 요청에서 DB 커넥션 풀 경쟁이 발생하기 쉬운 구조.
- 근거 코드:
  - `TicketController.enterQueue()`에서 DB 체크 2회
  - `TicketService.hasTicket()`, `TicketService.getTicketingStatus()`
  - Hikari pool: `maximum-pool-size: 30`

### 2.2 `GET /tickets/{eventId}/queue/status` 지연
- 증상: p95 `1.65s` (목표 300ms 대비 크게 초과).
- 문제 상황:
  - API 자체는 Redis 조회 위주라 원래 빠른 경로지만, 폴링 호출량이 높아짐.
  - 같은 시점의 `queue_enter/reserve` 처리와 리소스 경합이 발생.
- 관찰:
  - 이번 실행에서 `queue_status` 호출 수가 높게 집계됨(폴링 누적).

### 2.3 `POST /tickets/{eventId}/activate` 지연
- 증상: p95 `1.41s`.
- 문제 상황:
  - 핵심 로직은 Lua 전환(`READY -> ACTIVE`)이라 가벼워야 하나, 고부하 시점의 전체 리소스 경합 영향.
  - 일부 만료/해제 시 슬롯 보충(backfill) 로직이 동작하며 추가 처리 발생 가능.
- 근거 코드:
  - `QueueStateService.activateIfReady()`
  - `QueueStateService.releaseActive()` / `backfillFreedSlotsIfOpen()`

### 2.4 `POST /tickets/{eventId}/reserve` 지연
- 증상: p95 `1.70s`.
- 문제 상황:
  - async 경로에서 claim 후 DB 트랜잭션으로
    - `ticket_issue_requests` insert
    - `outbox_events` insert
    를 수행.
  - 버스트 쓰기 구간에서 DB 대기/커밋 지연이 응답시간에 반영됨.
- 근거 코드:
  - `TicketController.reserveTicketAsync()`
  - `TicketIssueEnqueueService.enqueueIssueRequest()`

### 2.5 `GET /tickets/{eventId}/requests/{requestId}` 지연
- 증상: p95 `959.57ms`.
- 문제 상황:
  - 폴링마다 DB 조회(`findByRequestIdAndEventIdAndUserId`) 수행.
  - 비동기 완료가 늦을수록 폴링 횟수 증가 -> DB 경합 증가 -> 지연 악화.
- 근거 코드:
  - `TicketController.getRequestStatus()`
  - `TicketIssueRequestRepository.findByRequestIdAndEventIdAndUserId()`

## 3. 비동기 파이프라인 병목 (핵심)

### 3.1 `ticket_issue_requests` 완료 지연 큼
- 측정값:
  - `db.ticket_issue_requests.p95_ms = 2978.82ms`
  - `db.outbox_events.p95_ms = 1908.80ms`
- 해석:
  - 엔드투엔드에서 `issue request 생성 -> 완료(SUCCESS/FAILED)`까지 tail latency가 큼.

### 3.2 Outbox publisher 처리 주기/방식 한계
- 문제 상황:
  - 스케줄러가 `fixedDelay=1000ms`(1초 주기).
  - 배치 크기 `BATCH_SIZE=100`.
  - publish 시 `kafkaTemplate.send(...).get()`로 동기 대기(직렬 성격 강화).
- 영향:
  - 300건 버스트 시 outbox 대기열이 한 번에 소진되지 못하고 지연 누적 가능.

### 3.3 Kafka consumer 병렬도 낮음
- 관찰:
  - 토픽 파티션 3개이나, 현재 동일 consumer-id 1개가 모두 처리하는 형태로 보임.
  - `lag=0`이므로 적체는 없지만, 처리 완료까지의 tail latency는 커질 수 있음.

## 4. 교차 이슈

### 4.1 DB 중심 병목 징후
- `queue_enter`/`reserve`/`request_status`가 모두 DB 접근에 민감.
- 커넥션 풀 30에서 동시 burst 시 대기열 발생 가능.

### 4.2 개발 프로파일 로그 오버헤드
- `spring.jpa.show-sql: true` 설정으로 고부하 시 SQL 로그 I/O 부하가 증가.

## 5. 결론
- 이번 문제는 **정확성/성공률 문제**가 아니라 **지연 시간(SLO) 미달 문제**다.
- 가장 큰 병목 후보는:
  1. `ticket_issue_requests` 완료 지연 (async 완료 경로)
  2. `queue_enter`의 초기 DB 체크 구간
  3. outbox publish/consumer의 처리 직렬성

