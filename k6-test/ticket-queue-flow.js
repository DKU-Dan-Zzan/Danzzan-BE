import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const EVENT_ID = __ENV.EVENT_ID || '1';
const TARGET_VUS = Number(__ENV.TARGET_VUS || 100);
const ITERATIONS_PER_VU = Number(__ENV.ITERATIONS_PER_VU || 1);
const EXECUTOR = __ENV.EXECUTOR || 'per-vu-iterations';
const FLOW_MODE = __ENV.FLOW_MODE || 'full-flow';
const RAMP_UP = __ENV.RAMP_UP || '10s';
const HOLD = __ENV.HOLD || '30s';
const RAMP_DOWN = __ENV.RAMP_DOWN || '10s';
const POLL_INTERVAL_MS = Number(__ENV.POLL_INTERVAL_MS || 1000);
const POLL_TIMEOUT_MS = Number(__ENV.POLL_TIMEOUT_MS || 180000);
const REQUEST_POLL_INTERVAL_MS = Number(__ENV.REQUEST_POLL_INTERVAL_MS || 1000);
const REQUEST_POLL_TIMEOUT_MS = Number(__ENV.REQUEST_POLL_TIMEOUT_MS || 180000);
const TOKENS_FILE = __ENV.TOKENS_FILE || './tokens.json';
const ADMIN_TOKEN = __ENV.ADMIN_TOKEN || '';
const INIT_STOCK = __ENV.INIT_STOCK ? Number(__ENV.INIT_STOCK) : null;

const tokens = new SharedArray('ticketing_tokens', function () {
  const raw = JSON.parse(open(TOKENS_FILE));
  if (!Array.isArray(raw)) {
    throw new Error('tokens file must be a JSON array');
  }
  return raw.map((entry) => {
    if (typeof entry === 'string') {
      return entry;
    }
    if (entry && typeof entry.accessToken === 'string') {
      return entry.accessToken;
    }
    throw new Error('each token entry must be a string or an object with accessToken');
  });
});

export const options = {
  scenarios: {
    ticket_queue_flow:
      EXECUTOR === 'ramping-vus'
        ? {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
              { duration: RAMP_UP, target: TARGET_VUS },
              { duration: HOLD, target: TARGET_VUS },
              { duration: RAMP_DOWN, target: 0 },
            ],
            gracefulRampDown: '5s',
          }
        : {
            executor: 'per-vu-iterations',
            vus: TARGET_VUS,
            iterations: ITERATIONS_PER_VU,
            maxDuration: __ENV.MAX_DURATION || '5m',
          },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    'http_req_duration{name:queue_enter}': ['p(95)<500'],
    ...(FLOW_MODE === 'full-flow'
      ? {
          'http_req_duration{name:queue_status}': ['p(95)<300'],
          'http_req_duration{name:activate}': ['p(95)<500'],
          'http_req_duration{name:reserve}': ['p(95)<700'],
          'http_req_duration{name:request_status}': ['p(95)<700'],
          reserve_success_rate: ['rate>0'],
        }
      : {}),
  },
};

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

export function setup() {
  if (!ADMIN_TOKEN) {
    return;
  }

  // 이벤트를 OPEN 상태로 전환 + Redis stock 자동 초기화
  // (스케줄러와 동일한 EventOpenService.openNow() 사용)
  const response = http.patch(
    `${BASE_URL}/api/admin/events/${EVENT_ID}/open`,
    null,
    {
      headers: withJsonAuth(ADMIN_TOKEN),
      tags: { name: 'admin_open' },
    }
  );

  // 200 = 이번에 OPEN 전환 성공, 409 = 이미 OPEN (둘 다 정상)
  check(response, {
    'admin open ok': (r) => r.status === 200 || r.status === 409,
  });
}

export default function () {
  const token = tokens[(__VU - 1) % tokens.length];
  const authHeaders = withJsonAuth(token);

  const enterRes = http.post(
    `${BASE_URL}/tickets/${EVENT_ID}/queue/enter`,
    null,
    {
      headers: authHeaders,
      tags: { name: 'queue_enter' },
    }
  );

  const enterBody = parseJson(enterRes);
  check(enterRes, {
    'queue enter handled': (r) => r.status === 200,
  });

  if (!enterBody || !enterBody.status) {
    reserveFailed.add(1);
    reserveSuccessRate.add(false);
    return;
  }

  if (enterBody.status === 'WAITING') {
    enterWaiting.add(1);
  } else if (enterBody.status === 'ADMITTED') {
    enterAdmitted.add(1);
  } else if (handleTerminalStatus(enterBody.status)) {
    return;
  }

  if (FLOW_MODE === 'enter-only') {
    return;
  }

  let statusBody = enterBody;
  const waitStart = Date.now();

  while (statusBody.status === 'WAITING') {
    if (Date.now() - waitStart >= POLL_TIMEOUT_MS) {
      queueTimeout.add(1);
      reserveSuccessRate.add(false);
      return;
    }

    sleep(POLL_INTERVAL_MS / 1000);
    const statusRes = http.get(
      `${BASE_URL}/tickets/${EVENT_ID}/queue/status`,
      {
        headers: authHeaders,
        tags: { name: 'queue_status' },
      }
    );
    queuePolls.add(1);
    check(statusRes, {
      'queue status handled': (r) => r.status === 200,
    });

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

  const activateRes = http.post(
    `${BASE_URL}/tickets/${EVENT_ID}/activate`,
    null,
    {
      headers: authHeaders,
      tags: { name: 'activate' },
    }
  );
  const activateBody = parseJson(activateRes);

  if (activateRes.status !== 200 || !activateBody || activateBody.status !== 'ADMITTED') {
    reserveFailed.add(1);
    reserveSuccessRate.add(false);
    return;
  }
  activateSuccess.add(1);

  const reserveRes = http.post(
    `${BASE_URL}/tickets/${EVENT_ID}/reserve`,
    null,
    {
      headers: authHeaders,
      tags: { name: 'reserve' },
    }
  );

  if (reserveRes.status === 200) {
    reserveSuccess.add(1);
    reserveSuccessRate.add(true);
    check(reserveRes, {
      'reserve success body has queueNumber': (r) => {
        const body = parseJson(r);
        return body && typeof body.queueNumber === 'number';
      },
    });
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
      sleep(REQUEST_POLL_INTERVAL_MS / 1000);
      const requestStatusRes = http.get(
        `${BASE_URL}/tickets/${EVENT_ID}/requests/${requestId}`,
        {
          headers: authHeaders,
          tags: { name: 'request_status' },
        }
      );
      requestPolls.add(1);

      if (requestStatusRes.status === 404) {
        continue;
      }
      if (requestStatusRes.status !== 200) {
        reserveAsyncFailed.add(1);
        reserveFailed.add(1);
        reserveSuccessRate.add(false);
        return;
      }

      const requestBody = parseJson(requestStatusRes);
      if (!requestBody || !requestBody.status) {
        reserveAsyncFailed.add(1);
        reserveFailed.add(1);
        reserveSuccessRate.add(false);
        return;
      }

      if (requestBody.status === 'SUCCESS') {
        requestWaitTrend.add(Date.now() - requestWaitStart);
        reserveSuccess.add(1);
        reserveSuccessRate.add(true);
        return;
      }
      if (requestBody.status === 'FAILED' || requestBody.status === 'SOLD_OUT' || requestBody.status === 'ALREADY') {
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

function handleTerminalStatus(status) {
  if (status === 'SOLD_OUT') {
    terminalSoldOut.add(1);
    reserveSuccessRate.add(false);
    return true;
  }
  if (status === 'ALREADY') {
    terminalAlready.add(1);
    reserveSuccessRate.add(false);
    return true;
  }
  if (status === 'SUCCESS') {
    terminalSuccess.add(1);
    reserveSuccessRate.add(true);
    return true;
  }
  return false;
}

function parseJson(response) {
  try {
    return response.json();
  } catch (e) {
    return null;
  }
}

function withJsonAuth(token) {
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
  };
}
