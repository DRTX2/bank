// Load test for the money path: POST /api/v1/transfers through nginx, with real risk scoring (gRPC),
// double-entry booking, outbox and Kafka. Run with `make load` against the docker compose stack.
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://web:8080';
const CUSTOMERS = Number(__ENV.CUSTOMERS || 40);
const PASSWORD = 'Kuntur2026!';
const outcomes = new Counter('transfer_outcomes');

export const options = {
  scenarios: {
    transfers: {
      executor: 'ramping-arrival-rate',
      startRate: 10,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 300,
      stages: [
        { target: Number(__ENV.PEAK_RPS || 150), duration: '60s' },
        { target: Number(__ENV.PEAK_RPS || 150), duration: '60s' },
      ],
    },
  },
  thresholds: {
    'http_req_failed{name:transfer}': ['rate<0.01'],
    'http_req_duration{name:transfer}': ['p(95)<500', 'p(99)<1000'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function login(email) {
  const res = http.post(`${BASE}/api/v1/auth/login`, JSON.stringify({ tenantCode: 'ANDN', email, password: PASSWORD }), {
    headers: { 'Content-Type': 'application/json' },
  });
  if (res.status !== 200) throw new Error(`login ${email}: ${res.status} ${res.body}`);
  return res.json('accessToken');
}

// Fresh, funded customers: the load test never touches the demo users' balances.
export function setup() {
  const teller = login('cajero@andina.ec');
  const auth = { headers: { Authorization: `Bearer ${teller}`, 'Content-Type': 'application/json' } };
  const run = Date.now().toString(36);
  const customers = [];
  for (let i = 0; i < CUSTOMERS; i++) {
    const email = `load-${run}-${i}@andina.ec`;
    const res = http.post(`${BASE}/api/v1/customers`, JSON.stringify({
      email, fullName: `Carga ${i}`, nationalId: '1700000000', password: PASSWORD, corporate: false,
    }), auth);
    if (res.status !== 201) throw new Error(`onboard: ${res.status} ${res.body}`);
    http.post(`${BASE}/api/v1/accounts/${res.json('id')}/deposits`, JSON.stringify({ amount: 1000000 }), auth);
    customers.push({ accountId: res.json('id'), number: res.json('number'), token: login(email) });
  }
  return { customers };
}

export default function ({ customers }) {
  // Indices, not objects: k6 hands each VU its own copy of the setup data, so identity checks never match
  const i = Math.floor(Math.random() * customers.length);
  const j = (i + 1 + Math.floor(Math.random() * (customers.length - 1))) % customers.length;
  const from = customers[i];
  const to = customers[j];
  const res = http.post(`${BASE}/api/v1/transfers`, JSON.stringify({
    sourceAccountId: from.accountId,
    destinationAccountNumber: to.number,
    amount: Math.round(100 + Math.random() * 4900) / 100,
    remittanceInfo: 'Carga k6',
  }), {
    headers: {
      Authorization: `Bearer ${from.token}`,
      'Content-Type': 'application/json',
      'Idempotency-Key': `${__VU}-${__ITER}-${Date.now()}`,
    },
    tags: { name: 'transfer' },
  });
  check(res, { 'transfer accepted (201)': (r) => r.status === 201 });
  if (res.status === 201) outcomes.add(1, { status: res.json('status') });
}
