# 로컬 300명 티켓팅 부하 시나리오 (Kafka 단일 브로커)

## 목표
- 로컬에서 `app + mysql + redis + kafka(단일 KRaft)`를 띄우고,
- 300명 동시 유입으로 `enter -> queue/status -> activate -> reserve(async)` 전체 플로우를 검증한다.

## 실행 명령
레포 루트에서:

```bash
./k6-test/run_local_ticketing_300.sh
```

병목 분석 리포트까지 한 번에 생성:

```bash
./k6-test/run_and_report_local_ticketing_300.sh
```

리포트 출력 경로:
- `k6-test/reports/ticketing-bottleneck-<timestamp>.md`

## 기본 파라미터
- `EVENT_ID=1`
- `COUNT=300` (테스트 유저/토큰 수)
- `STOCK=300`
- `TARGET_VUS=300`
- `FLOW_MODE=full-flow`
- `BASE_URL=http://localhost:8080`

## 오버라이드 예시
```bash
EVENT_ID=2 STOCK=500 TARGET_VUS=300 MAX_DURATION=15m \
./k6-test/run_local_ticketing_300.sh
```

## 스크립트가 수행하는 작업
1. `docker compose up -d mysql redis kafka kafka-init app`
2. 앱 준비 대기 (`POST /user/login` 응답 코드 확인)
3. 테스트 유저 300명 seed (`TEST0001~`)
4. 이벤트 upsert + `OPEN` 전환
5. Redis/DB 큐 상태 + async 발급 아티팩트 초기화
6. 로그인 API 기반 fresh access token 300개 생성
7. k6 full-flow 실행

## 결과 확인 포인트
- `http_req_failed`
- `reserve_success_rate`
- `http_req_duration{name:queue_enter}`
- `http_req_duration{name:request_status}`
- `request_status_wait_ms`
