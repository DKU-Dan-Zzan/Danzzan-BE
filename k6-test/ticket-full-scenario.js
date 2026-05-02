import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

// ──────────────────────────────────────────────
// 시나리오: 종합 부하테스트
//
// 그룹 1 (5000명): 이미 로그인 완료, 바로 대기열 진입
// 그룹 2 (100명): 티켓팅 중 로그인 → 대기열 진입
// 그룹 3 (10명): 티켓팅 중 회원탈퇴
// ──────────────────────────────────────────────

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const EVENT_ID = __ENV.EVENT_ID || '1';
const PASSWORD = __ENV.PASSWORD || 'test1234';
const TOKENS_FILE = __ENV.TOKENS_FILE || './tokens_fresh_5000.json';
const POLL_TIMEOUT_MS = Number(__ENV.POLL_TIMEOUT_MS || 300000);
const REQUEST_POLL_TIMEOUT_MS = Number(__ENV.REQUEST_POLL_TIMEOUT_MS || 300000);

// 그룹 1용: 이미 발급된 토큰 (5000명)
const preTokens = new SharedArray('pre_tokens', function () {
  const raw = JSON.parse(open(TOKENS_FILE));
  return raw.map((e) => (typeof e === 'string' ? e : e.accessToken));
});

// 그룹 2용: 로그인할 유저 (100명, TEST4901~TEST5000)
const loginUsers = new SharedArray('login_users', function () {
  const users = [];
  for (let i = 4901; i <= 5000; i++) {
    users.push({ studentId: `TEST${String(i).padStart(4, '0')}`, password: PASSWORD });
  }
  return users;
});

// 그룹 3용: 회원탈퇴할 유저 (10명, TEST5001~TEST5010)
const withdrawUsers = new SharedArray('withdraw_users', function () {
  const users = [];
  for (let i = 5001; i <= 5010; i++) {
    users.push({ studentId: `TEST${String(i).padStart(4, '0')}`, password: PASSWORD });
  }
  return users;
});

export const options = {
  scenarios: {
    // 그룹 1: 5000명 - 이미 로그인, 바로 대기열 진입
    ready_users: {
      executor: 'per-vu-iterations',
      vus: 5000,
      iterations: 1,
      maxDuration: '10m',
      exec: 'readyUserFlow',
    },
    // 그룹 2: 100명 - 로그인 후 대기열 진입
    login_at_open: {
      executor: 'per-vu-iterations',
      vus: 100,
      iterations: 1,
      maxDuration: '10m',
      exec: 'loginAtOpenFlow',
    },
    // 그룹 3: 10명 - 로그인 후 회원탈퇴
    withdraw_users: {
      executor: 'per-vu-iterations',
      vus: 10,
      iterations: 1,
      maxDuration: '10m',
      exec: 'withdrawFlow',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    reserve_success_rate: ['rate>0'],
  },
};

// ── 메트릭 ──
const loginSuccess = new Counter('login_success');
const loginFailed = new Counter('login_failed');
const loginDuration = new Trend('login_duration_ms');
const withdrawSuccess = new Counter('withdraw_success');
const withdrawFailed = new Counter('withdraw_failed');
const withdrawDuration = new Trend('withdraw_duration_ms');
const enterWaiting = new Counter('queue_enter_waiting');
const enterAdmitted = new Counter('queue_enter_admitted');
const queuePolls = new Counter('queue_status_polls');
const activateSuccess = new Counter('activate_success');
const reserveSuccess = new Counter('reserve_success');
const reserveFailed = new Counter('reserve_failed');
const reserveAsyncAccepted = new Counter('reserve_async_accepted');
const reserveAsyncFailed = new Counter('reserve_async_failed');
const reserveAsyncTimeout = new Counter('reserve_async_timeout');
const terminalSoldOut = new Counter('queue_terminal_sold_out');
const terminalAlready = new Counter('queue_terminal_already');
const terminalSuccess = new Counter('queue_terminal_success');
const queueTimeout = new Counter('queue_timeout');
const reserveSuccessRate = new Rate('reserve_success_rate');
const pollWaitTrend = new Trend('queue_poll_wait_ms');
const requestPolls = new Counter('request_status_polls');
const requestWaitTrend = new Trend('request_status_wait_ms');

// ── 그룹 1: 이미 로그인한 유저 ──
export function readyUserFlow() {
  const token = preTokens[(__VU - 1) % preTokens.length];
  ticketingFlow(token);
}

// ── 그룹 2: 로그인 후 티켓팅 ──
export function loginAtOpenFlow() {
  const idx = (__VU - 1) % loginUsers.length;
  const user = loginUsers[idx];
  const token = doLogin(user.studentId, user.password);
  if (!token) return;
  ticketingFlow(token);
}

// ── 그룹 3: 로그인 후 회원탈퇴 ──
export function withdrawFlow() {
  const idx = (__VU - 1) % withdrawUsers.length;
  const user = withdrawUsers[idx];
  const token = doLogin(user.studentId, user.password);
  if (!token) return;

  // 회원탈퇴 API 호출
  const start = Date.now();
  const res = http.del(
    `${BASE_URL}/user/me`,
    null,
    { headers: withJsonAuth(token), tags: { name: 'withdraw' } }
  );
  withdrawDuration.add(Date.now() - start);

  if (res.status === 200 || res.status === 204) {
    withdrawSuccess.add(1);
  } else {
    withdrawFailed.add(1);
  }
}

// ── 로그인 ──
function doLogin(studentId, password) {
  const start = Date.now();
  const res = http.post(
    `${BASE_URL}/user/login`,
    JSON.stringify({ studentId, password }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } }
  );
  loginDuration.add(Date.now() - start);

  if (res.status !== 200) {
    loginFailed.add(1);
    return null;
  }

  const body = parseJson(res);
  if (!body || !body.accessToken) {
    loginFailed.add(1);
    return null;
  }

  loginSuccess.add(1);
  return body.accessToken;
}

// ── 티켓팅 플로우 (프론트와 동일한 순번 기반 폴링) ──
function ticketingFlow(token) {
  const authHeaders = withJsonAuth(token);

  // 1. 대기열 진입
  const enterRes = http.post(
    `${BASE_URL}/tickets/${EVENT_ID}/queue/enter`,
    null,
    { headers: authHeaders, tags: { name: 'queue_enter' } }
  );

  const enterBody = parseJson(enterRes);
  check(enterRes, { 'queue enter handled': (r) => r.status === 200 });

  if (!enterBody || !enterBody.status) {
    reserveFailed.add(1);
    reserveSuccessRate.add(false);
    return;
  }

  if (enterBody.status === 'WAITING') enterWaiting.add(1);
  else if (enterBody.status === 'ADMITTED') enterAdmitted.add(1);
  else if (handleTerminalStatus(enterBody.status)) return;

  // 2. 대기열 폴링 (프론트 동일 순번 기반)
  let statusBody = enterBody;
  const waitStart = Date.now();

  while (statusBody.status === 'WAITING') {
    if (Date.now() - waitStart >= POLL_TIMEOUT_MS) {
      queueTimeout.add(1);
      reserveSuccessRate.add(false);
      return;
    }

    const position = statusBody.position != null ? statusBody.position : null;
    sleep(getAdaptiveInterval(position) / 1000);

    const statusRes = http.get(
      `${BASE_URL}/tickets/${EVENT_ID}/queue/status`,
      { headers: authHeaders, tags: { name: 'queue_status' } }
    );
    queuePolls.add(1);
    check(statusRes, { 'queue status handled': (r) => r.status === 200 });

    statusBody = parseJson(statusRes);
    if (!statusBody || !statusBody.status) {
      reserveFailed.add(1);
      reserveSuccessRate.add(false);
      return;
    }
    if (handleTerminalStatus(statusBody.status)) {
      pollWaitTrend.add(Date.now() - waitStart);
      return;
    }
  }

  pollWaitTrend.add(Date.now() - waitStart);

  if (statusBody.status !== 'ADMITTED') {
    reserveFailed.add(1);
    reserveSuccessRate.add(false);
    return;
  }

  // 3. 활성화
  const activateRes = http.post(
    `${BASE_URL}/tickets/${EVENT_ID}/activate`,
    null,
    { headers: authHeaders, tags: { name: 'activate' } }
  );
  const activateBody = parseJson(activateRes);

  if (activateRes.status !== 200 || !activateBody || activateBody.status !== 'ADMITTED') {
    reserveFailed.add(1);
    reserveSuccessRate.add(false);
    return;
  }
  activateSuccess.add(1);

  // 4. 예매
  const reserveRes = http.post(
    `${BASE_URL}/tickets/${EVENT_ID}/reserve`,
    null,
    { headers: authHeaders, tags: { name: 'reserve' } }
  );

  if (reserveRes.status === 200) {
    reserveSuccess.add(1);
    reserveSuccessRate.add(true);
    return;
  }

  if (reserveRes.status === 202) {
    const reserveBody = parseJson(reserveRes);
    if (!reserveBody || reserveBody.status !== 'PROCESSING' || !reserveBody.requestId) {
      reserveAsyncFailed.add(1);
      reserveFailed.add(1);
      reserveSuccessRate.add(false);
      return;
    }

    reserveAsyncAccepted.add(1);
    const requestId = reserveBody.requestId;
    const requestWaitStart = Date.now();

    while (Date.now() - requestWaitStart < REQUEST_POLL_TIMEOUT_MS) {
      sleep(getAdaptiveInterval(null) / 1000);
      const reqRes = http.get(
        `${BASE_URL}/tickets/${EVENT_ID}/requests/${requestId}`,
        { headers: authHeaders, tags: { name: 'request_status' } }
      );
      requestPolls.add(1);

      if (reqRes.status === 404) continue;
      if (reqRes.status !== 200) {
        reserveAsyncFailed.add(1);
        reserveFailed.add(1);
        reserveSuccessRate.add(false);
        return;
      }

      const reqBody = parseJson(reqRes);
      if (!reqBody || !reqBody.status) {
        reserveAsyncFailed.add(1);
        reserveFailed.add(1);
        reserveSuccessRate.add(false);
        return;
      }

      if (reqBody.status === 'SUCCESS') {
        requestWaitTrend.add(Date.now() - requestWaitStart);
        reserveSuccess.add(1);
        reserveSuccessRate.add(true);
        return;
      }
      if (reqBody.status === 'FAILED' || reqBody.status === 'SOLD_OUT' || reqBody.status === 'ALREADY') {
        requestWaitTrend.add(Date.now() - requestWaitStart);
        reserveAsyncFailed.add(1);
        reserveFailed.add(1);
        reserveSuccessRate.add(false);
        return;
      }
    }

    requestWaitTrend.add(Date.now() - requestWaitStart);
    reserveAsyncTimeout.add(1);
    reserveFailed.add(1);
    reserveSuccessRate.add(false);
    return;
  }

  reserveFailed.add(1);
  reserveSuccessRate.add(false);
}

// ── 유틸 ──
function handleTerminalStatus(status) {
  if (status === 'SOLD_OUT') { terminalSoldOut.add(1); reserveSuccessRate.add(false); return true; }
  if (status === 'ALREADY') { terminalAlready.add(1); reserveSuccessRate.add(false); return true; }
  if (status === 'SUCCESS') { terminalSuccess.add(1); reserveSuccessRate.add(true); return true; }
  return false;
}

// 프론트(flow-utils.ts)와 동일한 순번 기반 폴링 간격
function getAdaptiveInterval(position) {
  if (position === null) return 1000;
  if (position <= 100) return 1000;
  if (position <= 1000) return 2000;
  return 3000;
}

function parseJson(response) {
  try { return response.json(); } catch (e) { return null; }
}

function withJsonAuth(token) {
  return { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
}
