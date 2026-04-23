# CODEX 중요 변경사항 (현재 컨텍스트, 2026-04-21)

## 1) Queue Enter 경로 단순화/원자화
- `POST /tickets/{eventId}/queue/enter`에서 컨트롤러 사전 조회(DB/Redis 분기)를 제거하고 Lua 결과 중심으로 처리.
- `enter_queue.lua`에서 상태 판단 + 큐 진입 + 스냅샷 생성을 한 경로로 처리.
- `QueueEnterSnapshot`에 `status`, `requestId`, `acceptedAt` 등 상태 전달 필드 반영.
- 관련 파일:
  - `src/main/java/com/danzzan/domain/ticket/controller/TicketController.java`
  - `src/main/java/com/danzzan/domain/ticket/service/QueueService.java`
  - `src/main/java/com/danzzan/domain/ticket/service/QueueServiceImpl.java`
  - `src/main/resources/redis/enter_queue.lua`
  - `src/main/java/com/danzzan/global/config/RedisLuaScriptConfig.java`

## 2) Request Status Redis-first 전환
- `request_status` 조회 시 Redis를 우선 조회하고, 미스 시 DB fallback 후 Redis 캐시 동기화.
- enqueue/consumer/compensation 경로에서 요청 상태를 Redis에 업데이트하도록 반영.
- 관련 파일:
  - `src/main/java/com/danzzan/domain/ticket/service/TicketIssueRequestStatusCacheService.java` (신규)
  - `src/main/java/com/danzzan/domain/ticket/service/TicketIssueEnqueueService.java`
  - `src/main/java/com/danzzan/domain/ticket/consumer/TicketIssueRequestedConsumer.java`
  - `src/main/java/com/danzzan/domain/ticket/service/TicketIssueCompensationService.java`
  - `src/main/java/com/danzzan/domain/ticket/redis/TicketRedisKeys.java`

## 3) Release Active 경량화
- `releaseActive` API에서 동기 backfill 로직을 제거하고 active set 정리만 수행하도록 단순화.
- 관련 파일:
  - `src/main/java/com/danzzan/domain/ticket/service/QueueStateServiceImpl.java`

## 4) Redis 핫키 완화 1차 적용
- `queue_enter` 응답에서 `queuePosition` 제거.
- `admitted-seq` 키 도입 및 READY 승격 시 갱신, 대기순번 계산은 `mySeq - admittedSeq` 기반으로 전환.
- 부하 스크립트 폴링에 backoff + jitter 적용, reset 스크립트에 `admitted-seq` 초기화 반영.
- 관련 파일:
  - `src/main/java/com/danzzan/domain/ticket/redis/TicketRedisKeys.java`
  - `src/main/resources/redis/admit_one_waiting_user.lua`
  - `src/main/java/com/danzzan/domain/ticket/service/QueueServiceImpl.java`
  - `k6-test/ticket-queue-flow.js`
  - `k6-test/reset_event_queue.py`

## 5) 현재 상태 메모
- 위 변경 기준으로 부하 테스트는 재실행/재측정 진행됨.
- `request_status`는 개선 구간이 확인됐지만, `queue_enter`/`queue_status`는 이벤트 단일 키 경합 영향이 여전히 큼.
