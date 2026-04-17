#!/usr/bin/env bash
set -euo pipefail

EVENT_ID="${EVENT_ID:-1}"
WINDOW_HOURS="${WINDOW_HOURS:-2}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
DB_NAME="${DB_NAME:-${MYSQL_DATABASE:-festival_test}}"
DB_USER="${DB_USER:-${MYSQL_USER:-admin}}"
DB_PASSWORD="${DB_PASSWORD:-${MYSQL_PASSWORD:-dan2026zzan!}}"
KAFKA_GROUP="${KAFKA_GROUP:-ticket-issue-consumer-v1}"
REPORT_DIR="${REPORT_DIR:-k6-test/reports}"
RUN_TEST="${RUN_TEST:-true}"

TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
RUN_LOG="${RUN_LOG:-${REPORT_DIR}/k6-run-${TIMESTAMP}.log}"
PROM_FILE="${PROM_FILE:-${REPORT_DIR}/prometheus-${TIMESTAMP}.txt}"
KAFKA_RAW_FILE="${KAFKA_RAW_FILE:-${REPORT_DIR}/kafka-group-${TIMESTAMP}.txt}"
ISSUE_LAT_FILE="${ISSUE_LAT_FILE:-${REPORT_DIR}/issue-latency-${TIMESTAMP}.tsv}"
OUTBOX_LAT_FILE="${OUTBOX_LAT_FILE:-${REPORT_DIR}/outbox-latency-${TIMESTAMP}.tsv}"
ISSUE_STATUS_FILE="${ISSUE_STATUS_FILE:-${REPORT_DIR}/issue-status-${TIMESTAMP}.tsv}"
OUTBOX_STATUS_FILE="${OUTBOX_STATUS_FILE:-${REPORT_DIR}/outbox-status-${TIMESTAMP}.tsv}"
REPORT_FILE="${REPORT_FILE:-${REPORT_DIR}/ticketing-bottleneck-${TIMESTAMP}.md}"

mkdir -p "${REPORT_DIR}"

run_mysql() {
  local sql="$1"
  docker compose exec -T mysql \
    mysql -N -B -u"${DB_USER}" -p"${DB_PASSWORD}" "${DB_NAME}" -e "${sql}" 2>/dev/null
}

if [[ "${RUN_TEST}" == "true" ]]; then
  echo "[1/5] running local 300 ticketing scenario"
  set +e
  ./k6-test/run_local_ticketing_300.sh 2>&1 | tee "${RUN_LOG}"
  RUN_EXIT_CODE=${PIPESTATUS[0]}
  set -e
else
  echo "[1/5] skipping test run (RUN_TEST=false)"
  RUN_EXIT_CODE=0
  if [[ ! -f "${RUN_LOG}" ]]; then
    echo "RUN_LOG not found: ${RUN_LOG}"
    exit 1
  fi
fi

echo "[2/5] collecting prometheus metrics"
if ! curl -fsS "${BASE_URL}/actuator/prometheus" -o "${PROM_FILE}"; then
  echo "prometheus scrape failed: ${BASE_URL}/actuator/prometheus"
  : > "${PROM_FILE}"
fi

echo "[3/5] collecting db metrics"
run_mysql "
WITH req AS (
  SELECT TIMESTAMPDIFF(MICROSECOND, created_at, completed_at)/1000.0 AS ms
  FROM ticket_issue_requests
  WHERE event_id = ${EVENT_ID}
    AND created_at >= NOW() - INTERVAL ${WINDOW_HOURS} HOUR
    AND completed_at IS NOT NULL
),
ordered AS (
  SELECT ms, ROW_NUMBER() OVER (ORDER BY ms) rn, COUNT(*) OVER() cnt
  FROM req
)
SELECT
  COALESCE(ROUND(AVG(ms), 2), 0),
  COALESCE(ROUND(MAX(CASE WHEN rn = CEIL(cnt * 0.95) THEN ms END), 2), 0),
  COUNT(*)
FROM ordered;
" > "${ISSUE_LAT_FILE}" || : > "${ISSUE_LAT_FILE}"

run_mysql "
WITH ob AS (
  SELECT TIMESTAMPDIFF(MICROSECOND, created_at, sent_at)/1000.0 AS ms
  FROM outbox_events
  WHERE created_at >= NOW() - INTERVAL ${WINDOW_HOURS} HOUR
    AND sent_at IS NOT NULL
),
ordered AS (
  SELECT ms, ROW_NUMBER() OVER (ORDER BY ms) rn, COUNT(*) OVER() cnt
  FROM ob
)
SELECT
  COALESCE(ROUND(AVG(ms), 2), 0),
  COALESCE(ROUND(MAX(CASE WHEN rn = CEIL(cnt * 0.95) THEN ms END), 2), 0),
  COUNT(*)
FROM ordered;
" > "${OUTBOX_LAT_FILE}" || : > "${OUTBOX_LAT_FILE}"

run_mysql "
SELECT status, COUNT(*)
FROM ticket_issue_requests
WHERE event_id = ${EVENT_ID}
  AND created_at >= NOW() - INTERVAL ${WINDOW_HOURS} HOUR
GROUP BY status
ORDER BY status;
" > "${ISSUE_STATUS_FILE}" || : > "${ISSUE_STATUS_FILE}"

run_mysql "
SELECT status, COUNT(*)
FROM outbox_events
WHERE created_at >= NOW() - INTERVAL ${WINDOW_HOURS} HOUR
GROUP BY status
ORDER BY status;
" > "${OUTBOX_STATUS_FILE}" || : > "${OUTBOX_STATUS_FILE}"

echo "[4/5] collecting kafka lag"
if ! docker compose exec -T kafka \
  /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server kafka:9092 \
  --describe \
  --group "${KAFKA_GROUP}" > "${KAFKA_RAW_FILE}" 2>/dev/null; then
  : > "${KAFKA_RAW_FILE}"
fi

KAFKA_LAG_TOTAL="$(
  awk 'NR>1 && NF>=6 {sum+=$6} END {print sum+0}' "${KAFKA_RAW_FILE}" 2>/dev/null || echo 0
)"

echo "[5/5] building bottleneck report"
python3 - "$RUN_LOG" "$PROM_FILE" "$REPORT_FILE" "$EVENT_ID" "$RUN_EXIT_CODE" "$ISSUE_LAT_FILE" "$OUTBOX_LAT_FILE" "$ISSUE_STATUS_FILE" "$OUTBOX_STATUS_FILE" "$KAFKA_LAG_TOTAL" "$WINDOW_HOURS" <<'PY'
import re
import sys
from pathlib import Path
from datetime import datetime

(
    run_log_path,
    prom_path,
    report_path,
    event_id,
    run_exit_code,
    issue_lat_path,
    outbox_lat_path,
    issue_status_path,
    outbox_status_path,
    kafka_lag_total,
    window_hours,
) = sys.argv[1:]

run_log = Path(run_log_path).read_text(encoding="utf-8", errors="ignore") if Path(run_log_path).exists() else ""
prom_text = Path(prom_path).read_text(encoding="utf-8", errors="ignore") if Path(prom_path).exists() else ""


def parse_duration_to_ms(raw: str):
    if not raw:
        return None
    token = raw.strip().rstrip(",")
    m = re.match(r"^([0-9]+(?:\.[0-9]+)?)(ms|s|m)$", token)
    if not m:
        return None
    value = float(m.group(1))
    unit = m.group(2)
    if unit == "ms":
        return value
    if unit == "s":
        return value * 1000.0
    if unit == "m":
        return value * 60_000.0
    return None


def parse_thresholds(text: str):
    lines = text.splitlines()
    thresholds = {}
    pattern = re.compile(r"^\s*(http_req_duration\{name:[^}]+\}|http_req_failed|reserve_success_rate)\s*$")
    verdict = re.compile(r"^\s*([✓✗])\s*'([^']+)'\s*(.*)$")
    for i, line in enumerate(lines):
        m = pattern.match(line)
        if not m:
            continue
        metric = m.group(1)
        next_line = lines[i + 1] if i + 1 < len(lines) else ""
        v = verdict.match(next_line)
        if v:
            thresholds[metric] = {
                "ok": v.group(1) == "✓",
                "rule": v.group(2),
                "value": v.group(3).strip(),
            }
    return thresholds


def parse_k6_p95(text: str):
    result = {}
    line_re = re.compile(r"\{ name:([a-z_]+) \}.*p\(95\)=([0-9]+(?:\.[0-9]+)?(?:ms|s|m))")
    for line in text.splitlines():
        m = line_re.search(line)
        if not m:
            continue
        name = m.group(1)
        p95_raw = m.group(2)
        result[name] = {"p95_raw": p95_raw, "p95_ms": parse_duration_to_ms(p95_raw)}
    return result


def parse_tsv_triplet(path: str):
    p = Path(path)
    if not p.exists():
        return {"avg_ms": 0.0, "p95_ms": 0.0, "count": 0}
    line = p.read_text(encoding="utf-8", errors="ignore").strip().splitlines()
    if not line:
        return {"avg_ms": 0.0, "p95_ms": 0.0, "count": 0}
    cols = line[0].split("\t")
    if len(cols) < 3:
        return {"avg_ms": 0.0, "p95_ms": 0.0, "count": 0}
    return {
        "avg_ms": float(cols[0]),
        "p95_ms": float(cols[1]),
        "count": int(float(cols[2])),
    }


def parse_status_tsv(path: str):
    p = Path(path)
    if not p.exists():
        return {}
    out = {}
    for line in p.read_text(encoding="utf-8", errors="ignore").splitlines():
        cols = line.split("\t")
        if len(cols) >= 2:
            out[cols[0]] = int(cols[1])
    return out


def parse_labels(label_block: str):
    return dict(re.findall(r'([a-zA-Z_][a-zA-Z0-9_]*)="([^"]*)"', label_block))


def parse_prom_http_avgs(text: str):
    interest = {
        ("POST", "/tickets/{eventId}/queue/enter"),
        ("GET", "/tickets/{eventId}/queue/status"),
        ("POST", "/tickets/{eventId}/activate"),
        ("POST", "/tickets/{eventId}/reserve"),
        ("GET", "/tickets/{eventId}/requests/{requestId}"),
    }
    sums = {}
    counts = {}
    metric_re = re.compile(r'^http_server_requests_seconds_(sum|count)\{(.+)\}\s+([0-9.eE+-]+)$')
    for line in text.splitlines():
        m = metric_re.match(line.strip())
        if not m:
            continue
        metric_type = m.group(1)
        labels = parse_labels(m.group(2))
        key = (labels.get("method", ""), labels.get("uri", ""))
        if key not in interest:
            continue
        value = float(m.group(3))
        if metric_type == "sum":
            sums[key] = sums.get(key, 0.0) + value
        else:
            counts[key] = counts.get(key, 0.0) + value
    out = {}
    for key in sorted(interest):
        count = counts.get(key, 0.0)
        sm = sums.get(key, 0.0)
        avg_ms = (sm / count) * 1000.0 if count > 0 else 0.0
        out[key] = {"count": int(count), "sum_seconds": sm, "avg_ms": round(avg_ms, 2)}
    return out


def parse_prom_point_values(text: str):
    wanted = {
        "outbox_pending_count",
        "outbox_oldest_pending_age_seconds",
        "outbox_publish_success_total",
        "outbox_publish_retry_total",
        "outbox_publish_failed_total",
        "ticket_compensation_pending_count",
    }
    values = {}
    point_re = re.compile(r"^([a-zA-Z_:][a-zA-Z0-9_:]*)\{[^}]*\}\s+([0-9.eE+-]+)$")
    for line in text.splitlines():
        m = point_re.match(line.strip())
        if not m:
            continue
        name = m.group(1)
        if name not in wanted:
            continue
        values[name] = float(m.group(2))
    return values


thresholds = parse_thresholds(run_log)
k6_p95 = parse_k6_p95(run_log)
issue_latency = parse_tsv_triplet(issue_lat_path)
outbox_latency = parse_tsv_triplet(outbox_lat_path)
issue_status = parse_status_tsv(issue_status_path)
outbox_status = parse_status_tsv(outbox_status_path)
http_avgs = parse_prom_http_avgs(prom_text)
prom_points = parse_prom_point_values(prom_text)

candidates = []
for metric_name in ["queue_enter", "queue_status", "activate", "reserve", "request_status"]:
    value = k6_p95.get(metric_name, {}).get("p95_ms")
    if value is not None:
        candidates.append((f"k6.{metric_name}.p95_ms", value))

candidates.append(("db.ticket_issue_requests.p95_ms", issue_latency["p95_ms"]))
candidates.append(("db.outbox_events.p95_ms", outbox_latency["p95_ms"]))

candidates_sorted = sorted(candidates, key=lambda x: x[1], reverse=True)
top3 = candidates_sorted[:3]

kafka_lag = int(float(kafka_lag_total))
notes = []
if top3:
    notes.append(f"최대 지연 후보: `{top3[0][0]}` ({top3[0][1]:.2f}ms)")
if kafka_lag > 0:
    notes.append(f"Kafka consumer lag 존재: {kafka_lag}")
else:
    notes.append("Kafka consumer lag 0 (브로커/컨슈머 적체 없음)")
if issue_latency["p95_ms"] > outbox_latency["p95_ms"]:
    notes.append("비동기 발급 완료 지연의 주 구간은 outbox publish 이후보다 issue 처리 단계에 더 가까움")
else:
    notes.append("outbox publish 지연이 issue 처리 지연보다 크거나 유사함")

now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

lines = []
lines.append("# 티켓팅 병목 분석 리포트")
lines.append("")
lines.append(f"- 생성 시각: {now}")
lines.append(f"- 이벤트 ID: {event_id}")
lines.append(f"- 분석 윈도우: 최근 {window_hours}시간")
lines.append(f"- 부하 실행 exit code: {run_exit_code} (k6 threshold 미충족 시 99 가능)")
lines.append("")
lines.append("## 1) 병목 후보 Top 3 (p95 ms)")
lines.append("")
for name, value in top3:
    lines.append(f"- {name}: {value:.2f}ms")
if not top3:
    lines.append("- 데이터 없음")
lines.append("")
lines.append("## 2) k6 임계치 결과")
lines.append("")
if thresholds:
    for metric, info in thresholds.items():
        mark = "PASS" if info["ok"] else "FAIL"
        lines.append(f"- {metric}: {mark} ({info['rule']}) {info['value']}")
else:
    lines.append("- k6 threshold 파싱 실패 또는 실행 로그 없음")
lines.append("")
lines.append("## 3) 서버 HTTP 평균 지연 (Prometheus)")
lines.append("")
for (method, uri), data in http_avgs.items():
    lines.append(
        f"- {method} {uri}: avg={data['avg_ms']}ms, count={data['count']}, sum={data['sum_seconds']:.3f}s"
    )
lines.append("")
lines.append("## 4) 비동기 처리 DB 지표")
lines.append("")
lines.append(
    f"- ticket_issue_requests latency: avg={issue_latency['avg_ms']:.2f}ms, p95={issue_latency['p95_ms']:.2f}ms, n={issue_latency['count']}"
)
lines.append(
    f"- outbox_events latency: avg={outbox_latency['avg_ms']:.2f}ms, p95={outbox_latency['p95_ms']:.2f}ms, n={outbox_latency['count']}"
)
lines.append(f"- ticket_issue_requests status: {issue_status if issue_status else '{}'}")
lines.append(f"- outbox_events status: {outbox_status if outbox_status else '{}'}")
lines.append("")
lines.append("## 5) Kafka/Outbox 상태")
lines.append("")
lines.append(f"- kafka lag(total): {kafka_lag}")
lines.append(f"- outbox_pending_count: {prom_points.get('outbox_pending_count', 0.0)}")
lines.append(
    f"- outbox_oldest_pending_age_seconds: {prom_points.get('outbox_oldest_pending_age_seconds', 0.0)}"
)
lines.append("")
lines.append("## 6) 해석")
lines.append("")
for note in notes:
    lines.append(f"- {note}")

Path(report_path).write_text("\n".join(lines) + "\n", encoding="utf-8")
print(f"report_written={report_path}")
if top3:
    print(f"top_bottleneck={top3[0][0]}:{top3[0][1]:.2f}ms")
PY

echo "report file: ${REPORT_FILE}"
