#!/usr/bin/env bash
set -euo pipefail

EVENT_ID="${EVENT_ID:-1}"
COUNT="${COUNT:-300}"
STOCK="${STOCK:-300}"
TARGET_VUS="${TARGET_VUS:-300}"
ITERATIONS_PER_VU="${ITERATIONS_PER_VU:-1}"
MAX_DURATION="${MAX_DURATION:-10m}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
PASSWORD="${PASSWORD:-test1234}"
STUDENT_ID_PREFIX="${STUDENT_ID_PREFIX:-TEST}"
TOKENS_FILE="${TOKENS_FILE:-k6-test/tokens_load_300_fresh.json}"
DB_NAME="${DB_NAME:-${MYSQL_DATABASE:-festival_test}}"
DB_USER="${DB_USER:-${MYSQL_USER:-admin}}"
DB_PASSWORD="${DB_PASSWORD:-${MYSQL_PASSWORD:-dan2026zzan!}}"

echo "[1/7] docker compose up (mysql, redis, kafka, app)"
docker compose up -d --build mysql redis kafka kafka-init app

echo "[2/7] waiting for app: ${BASE_URL}"
for i in $(seq 1 120); do
  status_code="$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "${BASE_URL}/user/login" \
    -H "Content-Type: application/json" \
    -d '{}' || true)"
  if [[ "${status_code}" != "000" ]]; then
    echo "app is ready"
    break
  fi
  sleep 2
  if [[ "$i" == "120" ]]; then
    echo "app readiness timeout"
    exit 1
  fi
done

echo "[3/7] seed ${COUNT} users"
COUNT="${COUNT}" PASSWORD="${PASSWORD}" STUDENT_ID_PREFIX="${STUDENT_ID_PREFIX}" \
  ./k6-test/.venv/bin/python k6-test/seed_test_users.py

echo "[4/7] upsert load-test event id=${EVENT_ID} capacity=${STOCK}"
docker compose exec -T mysql mysql -u"${DB_USER}" -p"${DB_PASSWORD}" "${DB_NAME}" -e "
INSERT INTO festival_events (id, title, event_date, ticketing_start_time, ticketing_status, total_capacity)
VALUES (${EVENT_ID}, 'Local Load Test 300', CURDATE(), NOW() - INTERVAL 1 MINUTE, 'OPEN', ${STOCK})
ON DUPLICATE KEY UPDATE
  title = VALUES(title),
  event_date = VALUES(event_date),
  ticketing_start_time = VALUES(ticketing_start_time),
  ticketing_status = 'OPEN',
  total_capacity = VALUES(total_capacity);
"

echo "[5/7] reset queue/issue artifacts + stock=${STOCK}"
EVENT_ID="${EVENT_ID}" STOCK="${STOCK}" LIMIT="${COUNT}" STUDENT_ID_PREFIX="${STUDENT_ID_PREFIX}" \
  ./k6-test/.venv/bin/python k6-test/reset_event_queue.py

echo "[6/7] generate ${COUNT} fresh tokens -> ${TOKENS_FILE}"
BASE_URL="${BASE_URL}" COUNT="${COUNT}" PASSWORD="${PASSWORD}" STUDENT_ID_PREFIX="${STUDENT_ID_PREFIX}" \
  OUTPUT_FILE="${TOKENS_FILE}" python3 k6-test/generate_tokens_via_login.py

echo "[7/7] run k6 scenario (full-flow, async-aware)"
TOKENS_FILE_ABS="$(pwd)/${TOKENS_FILE}"
k6 run \
  -e TOKENS_FILE="${TOKENS_FILE_ABS}" \
  -e BASE_URL="${BASE_URL}" \
  -e EVENT_ID="${EVENT_ID}" \
  -e TARGET_VUS="${TARGET_VUS}" \
  -e ITERATIONS_PER_VU="${ITERATIONS_PER_VU}" \
  -e EXECUTOR="per-vu-iterations" \
  -e FLOW_MODE="full-flow" \
  -e POLL_TIMEOUT_MS=240000 \
  -e REQUEST_POLL_TIMEOUT_MS=240000 \
  -e MAX_DURATION="${MAX_DURATION}" \
  k6-test/ticket-queue-flow.js
