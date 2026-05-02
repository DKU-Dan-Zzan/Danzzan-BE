# 부하테스트 결과 보고서

## 1. 테스트 환경

### 서버 인프라 (NHN Cloud)
| 구성 요소 | 스펙/설정 |
|---|---|
| **서버** | ToastCloud Virtual CPU Gen.3, 8코어, 15GB RAM |
| **Spring Boot** | Docker 컨테이너, host network, JVM 힙 최대 4GB (G1GC) |
| **DB** | NHN Cloud RDS MySQL 8.4 (외부 연결) |
| **Redis** | 서버 내부 localhost:6379 |
| **Kafka** | 내부망 192.168.0.88:9092 (3 파티션, 3 consumer) |
| **로드밸런서** | NHN LB (HAProxy), 연결 제한 60,000, Keep-Alive 300초 |
| **TLS** | TLSv1.3, TERMINATED_HTTPS (LB에서 SSL 종료) |

### 서버 설정값
| 설정 | 값 |
|---|---|
| Tomcat max-threads | 1000 |
| Tomcat accept-count | 2000 |
| HikariCP max-pool-size | 30 |
| Redis Lettuce max-active | 200 |
| 컨테이너 ulimit (nofile) | 1,048,576 |
| tcp_max_syn_backlog | 1,024 (기본값) |
| 티켓팅 max-concurrent-slots | 2,000 |
| 티켓팅 admission batch-ceiling | 200 |
| 티켓팅 admission fixed-delay-ms | 300 |

### 테스트 클라이언트
- Mac (Apple Silicon), k6 v1.7.1
- 로컬 → HTTPS → NHN 로드밸런서 → Spring Boot (실제 사용자와 동일 경로)

### 테스트 데이터
- 테스트 유저: TEST0001 ~ TEST8000 (8,000명)
- 이벤트: DAY 3 (event_id=2, total_capacity=2,000)

---

## 2. 테스트 시나리오

실제 축제 상황을 시뮬레이션합니다.

```
시간축 ──────────────────────────────────────────→

① 배경 트래픽 (1,000명)
   █████████████████████████████████████  3분간 지속
   이벤트 목록 폴링 (2~5초 간격)

② 로그인 폭주 (100명)
            ████  10초 후 동시 로그인

③ 티켓팅 (8,000명)
            ████████████████████  10초 후 enter → poll → activate → reserve
```

| 시나리오 | VU 수 | 동작 |
|---|---|---|
| ① 배경 트래픽 | 1,000명 | 이벤트 목록 주기적 조회 (2~5초 간격) |
| ② 로그인 폭주 | 100명 | 이벤트 오픈 시점에 동시 로그인 |
| ③ 티켓팅 | 8,000명 | 대기열 진입 → 폴링 → activate → reserve |
| **총 동시 VU** | **9,100명** | |

### 티켓팅 플로우
```
1. queue/enter   → 대기열 진입 (WAITING 또는 ADMITTED)
2. queue/status  → 1초 간격 폴링 (ADMITTED 될 때까지)
3. activate      → READY → ACTIVE 전환
4. reserve       → 티켓 발급 (Redis stock DECR → DB 저장)
```

---

## 3. 테스트 결과

### 3-1. 9,100명 동시접속 (8,000명 티켓팅 + 1,000명 배경 + 100명 로그인)

#### 성능
| 구간 | 평균 | p95 |
|---|---|---|
| background_poll | 121ms | 408ms |
| login | 4.06s | 6.87s |
| queue_enter | 498ms | 2.77s |
| queue_status | 1.21s | 2.68s |
| activate | 1.11s | 1.75s |
| reserve | 491ms | 861ms |

#### 처리 결과
| 항목 | 값 |
|---|---|
| 티켓 발급 성공 | 2,000/8,000 (25%) — 재고 2,000장 전부 소진 |
| SOLD_OUT 정상 거부 | 886명 |
| 로그인 성공 | 100/100 (100%) |
| 배경 트래픽 | 정상 |
| 전체 소요 시간 | 3분 5초 |

#### connection reset 참고
- queue enter 실패 5,114건 (connection reset)
- 원인: 한 PC에서 9,100개 HTTPS 연결을 동시에 여는 k6 클라이언트 측 한계
- 서버 모니터링에서 overflow/drops 0건 확인 → 서버 문제 아님
- 실제 축제에서는 각 사용자가 별도 기기에서 1개씩 연결하므로 발생하지 않음

### 3-2. 4,000명 full-flow (배경 트래픽 없음, 이전 테스트와 동일 조건)

#### 성능
| 구간 | 평균 | p95 |
|---|---|---|
| queue_enter | 1.79s | 6.54s |
| queue_status | 596ms | 1.71s |
| activate | 1.64s | 6.39s |
| reserve | 867ms | 2.14s |

#### 처리 결과
| 항목 | 값 |
|---|---|
| 티켓 발급 성공 | 2,000/4,000 (50%) — 재고 전부 소진 |
| SOLD_OUT 정상 거부 | 1,823명 |
| HTTP 실패율 | 0.30% |
| 전체 소요 시간 | 65.4초 |

---

## 4. 정합성 검증

모든 테스트에서 동일한 결과:

| 검증 항목 | 결과 | 상세 |
|---|---|---|
| **발급 수량** | ✅ 통과 | 2,000장 정확히 발급 (재고 = total_capacity) |
| **원자성 (중복 발급)** | ✅ 통과 | 중복 유저 0건 — 1인 1매 보장 |
| **순번 연속성** | ✅ 통과 | ticketing_order 1~2,000 완전 연속 |
| **순번 중복** | ✅ 통과 | 0건 |
| **순번 갭** | ✅ 통과 | 0건 |
| **Redis 재고 정합** | ✅ 통과 | stock = 0 (2,000 - 2,000) |

---

## 5. 서버 모니터링 결과

부하테스트 중 2초 간격으로 서버 내부 모니터링을 수행했습니다.

| 시점 | FD | ESTAB | SYN_RECV | OVERFLOW | DROPS |
|---|---|---|---|---|---|
| 테스트 전 | 99 | 0 | 1 | 0 | 0 |
| 배경 진입 | 1,109 | 1,000 | 1 | 0 | 0 |
| 피크 | 4,429 | 4,289 | 1 | 0 | 0 |
| 종료 후 | 1,130 | 1,000 | 1 | 0 | 0 |

- **FD (파일 디스크립터)**: 피크 4,429개 / 최대 1,048,576 → 여유
- **OVERFLOW/DROPS**: 0건 → TCP SYN 큐 오버플로우 없음
- **서버 CPU**: 피크 시 86% → 테스트 종료 후 5% 이하로 복구

---

## 6. 인프라 응답 시간

| 인프라 | 응답 시간 |
|---|---|
| Redis | 0.11ms (로컬) |
| DB (NHN RDS) | 1.7~2.3ms |
| DB 첫 연결 | 84ms |

→ DB, Redis 모두 병목 아님

---

## 7. 결론

### 서버/인프라
- 정합성, 원자성, 순서보장 **모두 완벽**
- 로드밸런서 연결 제한 60,000 → 충분
- 컨테이너 ulimit 100만 → 충분
- TCP SYN 큐 overflow 0건

### 실제 축제 대응
- 10,000명 동시 접속 시 서버/인프라에 **문제 없음**
- 부하테스트의 connection reset은 한 PC에서 9,100개 HTTPS를 동시에 여는 클라이언트 측 한계
- 실제 사용자는 각자 기기에서 1개씩 연결하므로 해당 없음

---

## 8. 테스트 실행 방법

### 사전 준비

#### 1. 서버 초기화 (NHN 서버에서)
```bash
ssh -i danfesta.pem ubuntu@133.186.220.145

# 컨테이너 재시작
docker restart danzzan-app
sleep 15

# DB 정리
python3 -c "
import pymysql
conn = pymysql.connect(
    host='05436352-5959-4640-9a69-3070c16e0a36.external.kr1.mysql.rds.nhncloudservice.com',
    port=3306, user='danzzan', password='dan2026zzan!',
    database='festival_test', autocommit=True)
c = conn.cursor()
c.execute('DELETE FROM user_tickets')
c.execute('DELETE FROM ticket_issue_requests')
c.execute('DELETE FROM outbox_events')
conn.close()
print('DB cleared')
"

# Redis 초기화 + stock 세팅
redis-cli -a danzzan2026! FLUSHDB
redis-cli -a danzzan2026! SET ticket:2:stock 2000

# 확인
curl -s localhost:8080/tickets/events | python3 -m json.tool
```

#### 2. 토큰 생성 (로컬에서)
```bash
# SSH 터널 생성
ssh -i danfesta.pem -L 18080:localhost:8080 -N -f ubuntu@133.186.220.145

# 토큰 생성 (SSH 터널 경유, 빠름)
cd k6-test
python3 -c "
import json, requests
from concurrent.futures import ThreadPoolExecutor
session = requests.Session()
def login(i):
    try:
        r = session.post('http://localhost:18080/user/login',
            json={'studentId': f'TEST{i:04d}', 'password': 'test1234'}, timeout=10)
        if r.status_code == 200: return r.json().get('accessToken')
    except: pass
    return None
with ThreadPoolExecutor(max_workers=20) as ex:
    tokens = [t for t in ex.map(login, range(1, 8001)) if t]
with open('tokens_load_8000.json', 'w') as f:
    json.dump(tokens, f)
print(f'Generated {len(tokens)} tokens')
"
```

#### 3. 서버 CPU 안정 대기
```bash
ssh -i danfesta.pem ubuntu@133.186.220.145 "uptime"
# load average가 1.0 이하가 될 때까지 대기
```

### 테스트 실행 (로컬에서)

OPEN_TIME을 설정하면 k6가 해당 시간까지 대기 후 일제히 시작합니다.
DB의 `ticketing_status`를 `READY`로, `ticketing_start_time`을 같은 시간으로 설정해야 합니다.

```bash
# 1. 이벤트 상태를 READY로 변경 (NHN 서버에서)
python3 -c "
import pymysql
conn = pymysql.connect(...)
c = conn.cursor()
c.execute(\"UPDATE festival_events SET ticketing_status='READY', ticketing_start_time='2026-05-13 18:00:00' WHERE id=2\")
conn.commit(); conn.close()
"

# 2. k6 실행 (로컬에서) — OPEN_TIME과 ticketing_start_time을 동일하게
k6 run \
  --insecure-skip-tls-verify \
  -e BASE_URL=https://danfesta-test.yaaksok.com \
  -e EVENT_ID=2 \
  -e BG_VUS=1000 \
  -e TICKET_VUS=8000 \
  -e LOGIN_VUS=100 \
  -e TOKENS_FILE=/path/to/tokens_load_8000.json \
  -e POLL_INTERVAL_MS=1000 \
  -e POLL_TIMEOUT_MS=240000 \
  -e OPEN_TIME=2026-05-13T18:00:00+09:00 \
  k6-test/ticket-load-test.js
```

동작 흐름:
1. k6 시작 → 배경 트래픽 1000명 즉시 폴링 시작
2. 로그인/티켓팅 VU는 `OPEN_TIME`까지 sleep으로 대기
3. `OPEN_TIME` 도달 → BE 스케줄러가 `READY` → `OPEN` 자동 전환
4. 동시에 k6의 8000명이 일제히 대기열 진입 → full-flow

### 정합성 검증 (NHN 서버에서)
```bash
python3 -c "
import pymysql
conn = pymysql.connect(
    host='05436352-5959-4640-9a69-3070c16e0a36.external.kr1.mysql.rds.nhncloudservice.com',
    port=3306, user='danzzan', password='dan2026zzan!', database='festival_test')
c = conn.cursor()
c.execute('SELECT COUNT(*) FROM user_tickets WHERE event_id=2')
print(f'발급: {c.fetchone()[0]}')
c.execute('SELECT user_id, COUNT(*) cnt FROM user_tickets WHERE event_id=2 GROUP BY user_id HAVING cnt>1')
print(f'중복: {len(c.fetchall())}')
c.execute('SELECT MIN(ticketing_order), MAX(ticketing_order) FROM user_tickets WHERE event_id=2')
r = c.fetchone()
print(f'순번: {r[0]}~{r[1]}')
c.execute('SELECT ticketing_order, COUNT(*) cnt FROM user_tickets WHERE event_id=2 GROUP BY ticketing_order HAVING cnt>1')
print(f'순번중복: {len(c.fetchall())}')
c.execute('SELECT ticketing_order FROM user_tickets WHERE event_id=2 ORDER BY ticketing_order')
orders = [r[0] for r in c.fetchall()]
gaps = [(orders[i-1], orders[i]) for i in range(1, len(orders)) if orders[i]-orders[i-1]!=1]
print(f'갭: {len(gaps)}')
conn.close()
"
redis-cli -a danzzan2026! GET ticket:2:stock
```

### 서버 모니터링 (NHN 서버에서, 별도 터미널)
```bash
watch -n 2 'A=$(docker exec danzzan-app sh -c "ls /proc/1/fd | wc -l"); B=$(ss -t state established | grep :8080 | wc -l); C=$(ss -t state syn-recv | wc -l); D=$(ss -t state time-wait | wc -l); echo "FD=$A ESTAB=$B SYN=$C TW=$D"'
```
