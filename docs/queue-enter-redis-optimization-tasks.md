# Queue Enter Redis 최적화 태스크

## 목표
- `POST /tickets/{eventId}/queue/enter`의 Redis round-trip 수를 줄여 p95 지연을 낮춘다.
- 기능 정합성(중복 진입 방지, 상태 일관성)은 유지한다.

## 1) queue_enter 응답 경량화 (우선 적용)
- 변경 내용
  - `POST /queue/enter` 응답 필드를 `status`, `queuePosition` 중심으로 최소화한다.
  - `mySequence`, `aheadCount`, `estimatedWaitSeconds`, `readyUntil`, `admissionState`는 `GET /queue/status`에서만 조회한다.
- 기대 효과
  - `queue_enter` 요청 당 불필요한 Redis 조회 제거
  - 진입 API의 응답 생성 비용 감소
- 리스크/대응
  - 프론트가 `queue_enter`에서 상세 필드를 기대하면 깨질 수 있음
  - 대응: 상세 정보는 `queue/status`에서 조회하도록 계약 명시
- 상태
  - 완료

## 2) enter + snapshot 단일 Lua화
- 변경 내용
  - `enterQueue`와 초기 스냅샷 조회를 하나의 Lua로 합쳐 원자 처리
  - 반환값에 `status`, `queuePosition`, `seq` 등을 포함해 서버 재조회 제거
- 기대 효과
  - Redis 왕복 횟수 큰 폭 감소
  - 진입 시점 race window 축소
- 리스크/대응
  - Lua 복잡도 증가
  - 대응: 반환 프로토콜 고정 + 단위테스트 강화
- 상태
  - 미착수

## 3) 중복 조회 제거
- 변경 내용
  - `queuePosition`, `state` 등 같은 데이터 재조회 제거
  - 동일 요청 내 한 번 읽은 값은 재사용
- 기대 효과
  - 소규모지만 즉시 가능한 성능 개선
- 리스크/대응
  - 로직 분기에서 null 처리 누락 가능
  - 대응: 케이스별 테스트 보강
- 상태
  - 미착수

## 4) HMGET/파이프라인 적용
- 변경 내용
  - 다건 `HGET`를 `HMGET`로 통합
  - 가능한 조회를 파이프라인으로 묶어 RTT 절감
- 기대 효과
  - 네트워크 왕복 비용 절감
- 리스크/대응
  - 코드 복잡도/가독성 저하
  - 대응: 래퍼 메서드로 캡슐화
- 상태
  - 미착수
