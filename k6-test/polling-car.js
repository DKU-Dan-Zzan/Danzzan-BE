import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const EVENT_ID = __ENV.EVENT_ID || '2';
const RATE = Number(__ENV.RATE || 100);
const DURATION = __ENV.DURATION || '30s';
const PRE_ALLOCATED_VUS = Number(__ENV.PRE_ALLOCATED_VUS || Math.max(200, RATE));
const MAX_VUS = Number(__ENV.MAX_VUS || Math.max(PRE_ALLOCATED_VUS * 2, RATE * 3));
const TOKENS_FILE = __ENV.TOKENS_FILE || './tokens_load_500_fresh_now.json';

const tokens = new SharedArray('polling_car_tokens', function () {
  const raw = JSON.parse(open(TOKENS_FILE));
  if (!Array.isArray(raw)) {
    throw new Error('tokens file must be a JSON array');
  }
  return raw.map((entry) => (typeof entry === 'string' ? entry : entry.accessToken));
});

function authHeaders(token) {
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
  };
}

export const options = {
  scenarios: {
    queue_status_car: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: PRE_ALLOCATED_VUS,
      maxVUs: MAX_VUS,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:queue_status}': ['p(95)<1000'],
  },
};

export default function () {
  const idx = exec.scenario.iterationInTest % tokens.length;
  const token = tokens[idx];
  const response = http.get(
    `${BASE_URL}/tickets/${EVENT_ID}/queue/status`,
    { headers: authHeaders(token), tags: { name: 'queue_status' } }
  );

  check(response, {
    'queue status handled': (r) => r.status === 200,
  });
}
