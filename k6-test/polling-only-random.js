import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const EVENT_ID = __ENV.EVENT_ID || '1';
const TARGET_VUS = Number(__ENV.TARGET_VUS || 500);
const ITERATIONS_PER_VU = Number(__ENV.ITERATIONS_PER_VU || 1);
const POLL_COUNT = Number(__ENV.POLL_COUNT || 20);
const POLL_INTERVAL_SEC = Number(__ENV.POLL_INTERVAL_SEC || 0.5);
const POLL_JITTER_RATIO = Number(__ENV.POLL_JITTER_RATIO || 0.5);
const START_STAGGER_SEC = Number(__ENV.START_STAGGER_SEC || 2.0);
const TOKENS_FILE = __ENV.TOKENS_FILE || './tokens.json';

const tokens = new SharedArray('polling_tokens', function () {
  const raw = JSON.parse(open(TOKENS_FILE));
  if (!Array.isArray(raw)) {
    throw new Error('tokens file must be a JSON array');
  }
  return raw.map((entry) => (typeof entry === 'string' ? entry : entry.accessToken));
});

export const options = {
  scenarios: {
    polling_only_random: {
      executor: 'per-vu-iterations',
      vus: TARGET_VUS,
      iterations: ITERATIONS_PER_VU,
      maxDuration: __ENV.MAX_DURATION || '15m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    'http_req_duration{name:queue_status}': ['p(95)<500'],
  },
};

function authHeaders(token) {
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
  };
}

function randBetween(min, max) {
  return min + Math.random() * (max - min);
}

function jitteredDelaySec(baseSec, ratio) {
  const safeBase = Math.max(0, baseSec);
  const safeRatio = Math.max(0, ratio);
  const min = Math.max(0, safeBase * (1 - safeRatio));
  const max = safeBase * (1 + safeRatio);
  return randBetween(min, max);
}

export default function () {
  const token = tokens[(__VU - 1) % tokens.length];
  const headers = authHeaders(token);

  // VU 시작 시점을 분산시켜 동시 burst를 완화한다.
  if (START_STAGGER_SEC > 0) {
    sleep(randBetween(0, START_STAGGER_SEC));
  }

  for (let i = 0; i < POLL_COUNT; i += 1) {
    const response = http.get(
      `${BASE_URL}/tickets/${EVENT_ID}/queue/status`,
      { headers, tags: { name: 'queue_status' } }
    );
    check(response, { 'queue status handled': (r) => r.status === 200 });

    if (i < POLL_COUNT - 1) {
      sleep(jitteredDelaySec(POLL_INTERVAL_SEC, POLL_JITTER_RATIO));
    }
  }
}
