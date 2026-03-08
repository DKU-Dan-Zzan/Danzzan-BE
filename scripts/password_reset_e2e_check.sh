#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

APP_PORT="${APP_PORT:-18080}"
APP_URL="${APP_URL:-http://localhost:${APP_PORT}}"
APP_LOG_FILE="${APP_LOG_FILE:-/tmp/danzzan_password_reset_e2e_bootrun.log}"

MYSQL_CONTAINER="${MYSQL_CONTAINER:-dz174-e2e-mysql}"
MYSQL_PORT="${MYSQL_PORT:-13306}"
MYSQL_DB="${MYSQL_DB:-festival_test}"
MYSQL_USER="${MYSQL_USER:-admin}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-admin}"

REDIS_CONTAINER="${REDIS_CONTAINER:-dz174-e2e-redis}"
REDIS_PORT="${REDIS_PORT:-16379}"

MAILPIT_CONTAINER="${MAILPIT_CONTAINER:-dz174-e2e-mailpit}"
MAILPIT_SMTP_PORT="${MAILPIT_SMTP_PORT:-11025}"
MAILPIT_HTTP_PORT="${MAILPIT_HTTP_PORT:-18025}"
MAILPIT_API_URL="${MAILPIT_API_URL:-http://localhost:${MAILPIT_HTTP_PORT}}"

STUDENT_ID="${STUDENT_ID:-32100000}"
EMAIL="${EMAIL:-${STUDENT_ID}@dankook.ac.kr}"
OLD_PASSWORD="${OLD_PASSWORD:-OldPass!2026}"
NEW_PASSWORD="${NEW_PASSWORD:-NewPass!2026}"

APP_PID=""
APP_STARTED_BY_SCRIPT="0"

step() {
  echo
  echo "[$(date '+%H:%M:%S')] $1"
}

ok() {
  echo "✅ $1"
}

fail() {
  echo "❌ $1"
  if [[ -f "${APP_LOG_FILE}" ]]; then
    echo
    echo "---- app log tail (${APP_LOG_FILE}) ----"
    tail -n 80 "${APP_LOG_FILE}" || true
    echo "----------------------------------------"
  fi
  exit 1
}

cleanup() {
  if [[ "${APP_STARTED_BY_SCRIPT}" == "1" ]] && [[ -n "${APP_PID}" ]]; then
    if kill -0 "${APP_PID}" 2>/dev/null; then
      kill "${APP_PID}" 2>/dev/null || true
      wait "${APP_PID}" 2>/dev/null || true
    fi
  fi
}
trap cleanup EXIT

require_cmd() {
  local cmd="$1"
  command -v "${cmd}" >/dev/null 2>&1 || fail "필수 명령어가 없습니다: ${cmd}"
}

container_exists() {
  local name="$1"
  docker ps -a --format '{{.Names}}' | grep -Fxq "${name}"
}

container_running() {
  local name="$1"
  docker ps --format '{{.Names}}' | grep -Fxq "${name}"
}

ensure_container_running() {
  local name="$1"
  shift
  if container_exists "${name}"; then
    if ! container_running "${name}"; then
      docker start "${name}" >/dev/null
    fi
  else
    docker run --name "${name}" -d "$@" >/dev/null
  fi
}

wait_for_tcp() {
  local host="$1"
  local port="$2"
  local timeout_sec="$3"
  local i
  for ((i = 1; i <= timeout_sec; i++)); do
    if nc -z "${host}" "${port}" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  return 1
}

wait_for_http_200() {
  local url="$1"
  local timeout_sec="$2"
  local i
  for ((i = 1; i <= timeout_sec; i++)); do
    local code
    code="$(curl -s -o /dev/null -w "%{http_code}" "${url}" || true)"
    if [[ "${code}" == "200" ]]; then
      return 0
    fi
    sleep 1
  done
  return 1
}

wait_for_mysql_ready() {
  local timeout_sec="$1"
  local i
  for ((i = 1; i <= timeout_sec; i++)); do
    if docker exec "${MYSQL_CONTAINER}" mysqladmin ping -h127.0.0.1 -u"${MYSQL_USER}" -p"${MYSQL_PASSWORD}" --silent >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  return 1
}

wait_for_users_table() {
  local timeout_sec="$1"
  local i
  for ((i = 1; i <= timeout_sec; i++)); do
    local exists
    exists="$(docker exec "${MYSQL_CONTAINER}" mysql -u"${MYSQL_USER}" -p"${MYSQL_PASSWORD}" -Nse \
      "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${MYSQL_DB}' AND table_name='users';" 2>/dev/null || true)"
    if [[ "${exists}" == "1" ]]; then
      return 0
    fi
    sleep 2
  done
  return 1
}

clear_password_reset_state() {
  docker exec "${REDIS_CONTAINER}" redis-cli EVAL \
    "local keys=redis.call('KEYS', ARGV[1]); if #keys > 0 then redis.call('DEL', unpack(keys)); end return #keys;" \
    0 "password-reset:*" >/dev/null
}

extract_code_from_mailpit() {
  local request_id="$1"
  local recipient="$2"
  local attempts=90
  local i

  for ((i = 1; i <= attempts; i++)); do
    local list_json
    list_json="$(curl -fsS "${MAILPIT_API_URL}/api/v1/messages?limit=50" 2>/dev/null || true)"
    if [[ -n "${list_json}" ]]; then
      local ids_raw
      ids_raw="$(echo "${list_json}" | jq -r --arg to "${recipient}" '.messages[]? | select([.To[]?.Address] | index($to)) | .ID')"
      if [[ -n "${ids_raw}" ]]; then
        while IFS= read -r id; do
          [[ -z "${id}" ]] && continue
          local message_json
          message_json="$(curl -fsS "${MAILPIT_API_URL}/api/v1/message/${id}" 2>/dev/null || true)"
          [[ -z "${message_json}" ]] && continue
          local text
          text="$(echo "${message_json}" | jq -r '.Text // ""')"
          if [[ "${text}" == *"${request_id}"* ]]; then
            local code
            code="$(printf "%s" "${text}" | grep -Eo '[0-9]{6}' | head -n 1 || true)"
            if [[ -n "${code}" ]]; then
              echo "${code}"
              return 0
            fi
          fi
        done <<EOF
${ids_raw}
EOF
      fi
    fi
    sleep 2
  done
  return 1
}

http_post_json() {
  local url="$1"
  local payload="$2"
  local body_file
  body_file="$(mktemp)"
  local status
  status="$(curl -sS -o "${body_file}" -w "%{http_code}" -X POST "${url}" \
    -H "Content-Type: application/json" \
    -d "${payload}" || true)"
  local body
  body="$(cat "${body_file}")"
  rm -f "${body_file}"
  echo "${status}"$'\n'"${body}"
}

step "필수 커맨드 확인"
require_cmd docker
require_cmd curl
require_cmd jq
require_cmd nc
require_cmd htpasswd
ok "필수 커맨드 확인 완료"

step "E2E용 Docker 컨테이너 준비"
ensure_container_running "${REDIS_CONTAINER}" -p "${REDIS_PORT}:6379" redis:7
ensure_container_running "${MYSQL_CONTAINER}" \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE="${MYSQL_DB}" \
  -e MYSQL_USER="${MYSQL_USER}" \
  -e MYSQL_PASSWORD="${MYSQL_PASSWORD}" \
  -p "${MYSQL_PORT}:3306" mysql:8
ensure_container_running "${MAILPIT_CONTAINER}" \
  -p "${MAILPIT_HTTP_PORT}:8025" \
  -p "${MAILPIT_SMTP_PORT}:1025" \
  axllent/mailpit

wait_for_tcp "127.0.0.1" "${REDIS_PORT}" 30 || fail "Redis 포트(${REDIS_PORT}) 준비 실패"
wait_for_tcp "127.0.0.1" "${MAILPIT_HTTP_PORT}" 30 || fail "Mailpit HTTP 포트(${MAILPIT_HTTP_PORT}) 준비 실패"
wait_for_tcp "127.0.0.1" "${MAILPIT_SMTP_PORT}" 30 || fail "Mailpit SMTP 포트(${MAILPIT_SMTP_PORT}) 준비 실패"
wait_for_mysql_ready 90 || fail "MySQL 준비 실패"
ok "컨테이너 준비 완료"

step "Spring Boot 앱 기동"
if nc -z 127.0.0.1 "${APP_PORT}" >/dev/null 2>&1; then
  fail "포트 ${APP_PORT} 가 이미 사용 중입니다. APP_PORT를 바꿔서 다시 실행하세요."
fi

(
  cd "${ROOT_DIR}"
  SPRING_PROFILES_ACTIVE=local \
  SERVER_PORT="${APP_PORT}" \
  DB_URL="jdbc:mysql://localhost:${MYSQL_PORT}/${MYSQL_DB}?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Seoul" \
  DB_USERNAME="${MYSQL_USER}" \
  DB_PASSWORD="${MYSQL_PASSWORD}" \
  REDIS_HOST=localhost \
  REDIS_PORT="${REDIS_PORT}" \
  MAIL_HOST=localhost \
  MAIL_PORT="${MAILPIT_SMTP_PORT}" \
  MAIL_USERNAME=noreply@danzzan.com \
  MAIL_PASSWORD=dummy \
  MAIL_SMTP_AUTH=false \
  MAIL_SMTP_STARTTLS=false \
  ./gradlew bootRun --no-daemon
) >"${APP_LOG_FILE}" 2>&1 &
APP_PID=$!
APP_STARTED_BY_SCRIPT="1"

wait_for_http_200 "${APP_URL}/v3/api-docs" 180 || fail "앱 기동 실패 (${APP_URL})"
wait_for_users_table 120 || fail "users 테이블 준비 실패"
ok "앱 기동 완료 (${APP_URL})"

step "테스트 사용자 준비"
PASSWORD_HASH="$(htpasswd -bnBC 10 "" "${OLD_PASSWORD}" | tr -d ':\n')"
docker exec -i "${MYSQL_CONTAINER}" mysql -u"${MYSQL_USER}" -p"${MYSQL_PASSWORD}" "${MYSQL_DB}" <<SQL
INSERT INTO users (student_id, password, name, college, major, academic_status, role, token_version, created_at)
VALUES ('${STUDENT_ID}', '${PASSWORD_HASH}', 'E2E테스터', '공과대학', '컴퓨터공학과', 'ENROLLED', 'ROLE_USER', 0, NOW())
ON DUPLICATE KEY UPDATE password=VALUES(password), token_version=0;
SQL
ok "테스트 사용자 준비 완료 (${STUDENT_ID})"

step "Redis 비밀번호 재설정 상태 초기화"
clear_password_reset_state
ok "password-reset:* 키 초기화 완료"

step "1) request API 호출"
REQUEST_RESULT="$(http_post_json "${APP_URL}/user/password/reset/request" "{\"studentId\":\"${STUDENT_ID}\",\"email\":\"${EMAIL}\"}")"
REQUEST_STATUS="$(echo "${REQUEST_RESULT}" | head -n 1)"
REQUEST_BODY="$(echo "${REQUEST_RESULT}" | tail -n +2)"
echo "request status=${REQUEST_STATUS}, body=${REQUEST_BODY}"
[[ "${REQUEST_STATUS}" == "200" ]] || fail "request API 실패"
REQUEST_ID="$(echo "${REQUEST_BODY}" | jq -r '.requestId // empty')"
[[ -n "${REQUEST_ID}" ]] || fail "requestId 파싱 실패"
ok "request 성공 requestId=${REQUEST_ID}"

step "2) Mailpit에서 인증코드 추출"
CODE="$(extract_code_from_mailpit "${REQUEST_ID}" "${EMAIL}" || true)"
[[ -n "${CODE}" ]] || fail "Mailpit에서 인증코드 추출 실패"
ok "인증코드 추출 성공 code=${CODE}"

step "3) verify API 호출"
VERIFY_RESULT="$(http_post_json "${APP_URL}/user/password/reset/verify" "{\"requestId\":\"${REQUEST_ID}\",\"code\":\"${CODE}\"}")"
VERIFY_STATUS="$(echo "${VERIFY_RESULT}" | head -n 1)"
VERIFY_BODY="$(echo "${VERIFY_RESULT}" | tail -n +2)"
echo "verify status=${VERIFY_STATUS}, body=${VERIFY_BODY}"
[[ "${VERIFY_STATUS}" == "200" ]] || fail "verify API 실패"
VERIFICATION_TOKEN="$(echo "${VERIFY_BODY}" | jq -r '.verificationToken // empty')"
[[ -n "${VERIFICATION_TOKEN}" ]] || fail "verificationToken 파싱 실패"
ok "verify 성공"

step "4) reset API 호출"
RESET_RESULT="$(http_post_json "${APP_URL}/user/password/reset" "{\"requestId\":\"${REQUEST_ID}\",\"verificationToken\":\"${VERIFICATION_TOKEN}\",\"newPassword\":\"${NEW_PASSWORD}\"}")"
RESET_STATUS="$(echo "${RESET_RESULT}" | head -n 1)"
RESET_BODY="$(echo "${RESET_RESULT}" | tail -n +2)"
echo "reset status=${RESET_STATUS}, body=${RESET_BODY}"
[[ "${RESET_STATUS}" == "200" ]] || fail "reset API 실패"
ok "reset 성공"

step "5) 구 비밀번호 로그인 실패 확인"
OLD_LOGIN_RESULT="$(http_post_json "${APP_URL}/user/login" "{\"studentId\":\"${STUDENT_ID}\",\"password\":\"${OLD_PASSWORD}\"}")"
OLD_LOGIN_STATUS="$(echo "${OLD_LOGIN_RESULT}" | head -n 1)"
OLD_LOGIN_BODY="$(echo "${OLD_LOGIN_RESULT}" | tail -n +2)"
echo "old login status=${OLD_LOGIN_STATUS}, body=${OLD_LOGIN_BODY}"
[[ "${OLD_LOGIN_STATUS}" == "401" ]] || fail "구 비밀번호 로그인 실패 검증 실패"
ok "구 비밀번호 로그인 실패 확인"

step "6) 신 비밀번호 로그인 성공 확인"
NEW_LOGIN_RESULT="$(http_post_json "${APP_URL}/user/login" "{\"studentId\":\"${STUDENT_ID}\",\"password\":\"${NEW_PASSWORD}\"}")"
NEW_LOGIN_STATUS="$(echo "${NEW_LOGIN_RESULT}" | head -n 1)"
NEW_LOGIN_BODY="$(echo "${NEW_LOGIN_RESULT}" | tail -n +2)"
echo "new login status=${NEW_LOGIN_STATUS}, body=${NEW_LOGIN_BODY}"
[[ "${NEW_LOGIN_STATUS}" == "200" ]] || fail "신 비밀번호 로그인 성공 검증 실패"
ACCESS_TOKEN="$(echo "${NEW_LOGIN_BODY}" | jq -r '.accessToken // empty')"
[[ -n "${ACCESS_TOKEN}" ]] || fail "신 비밀번호 로그인 응답 accessToken 없음"
ok "신 비밀번호 로그인 성공 확인"

echo
echo "=============================="
echo "✅ E2E PASS: request -> verify -> reset -> login(old fail/new success)"
echo "App URL     : ${APP_URL}"
echo "Mailpit URL : http://localhost:${MAILPIT_HTTP_PORT}"
echo "=============================="
