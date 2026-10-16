// k6 load test for order creation (the write path: API -> Postgres + outbox -> Kafka -> dispatch).
// Usage: k6 run -e BASE_URL=http://localhost:8080 -e EMAIL=demo@fleetflow.dev -e PASSWORD=... -e RATE=100 create-orders.js
// Run with RATE=10000 only after sizing the backend and database for that load.
// Start the backend with RATE_LIMIT_ORDERS=1000000 or the per-user limiter will (correctly) return 429.
// No thresholds are asserted on purpose: record what you measure in the README table.
import http from 'k6/http'
import { check } from 'k6'
import { Trend, Counter } from 'k6/metrics'

const RATE = Number(__ENV.RATE || 100)
const PRE_ALLOCATED_VUS = Number(__ENV.PREALLOCATED_VUS || Math.max(50, Math.min(RATE, 1000)))
const MAX_VUS = Number(__ENV.MAX_VUS || Math.max(PRE_ALLOCATED_VUS, RATE * 4))

if (!Number.isInteger(RATE) || RATE < 1) {
  throw new Error('RATE must be a positive integer')
}
if (!Number.isInteger(PRE_ALLOCATED_VUS) || PRE_ALLOCATED_VUS < 1) {
  throw new Error('PREALLOCATED_VUS must be a positive integer')
}
if (!Number.isInteger(MAX_VUS) || MAX_VUS < PRE_ALLOCATED_VUS) {
  throw new Error('MAX_VUS must be an integer greater than or equal to PREALLOCATED_VUS')
}

export const options = {
  scenarios: { orders: { executor: 'constant-arrival-rate', rate: RATE, timeUnit: '1s', duration: __ENV.DURATION || '60s', preAllocatedVUs: PRE_ALLOCATED_VUS, maxVUs: MAX_VUS } },
}
const createLatency = new Trend('order_create_latency', true)
const rateLimited = new Counter('order_create_rate_limited')
const unauthorized = new Counter('order_create_unauthorized')
const BASE = __ENV.BASE_URL || 'http://localhost:8080'
const places = [[30.7398, 76.7827], [30.7046, 76.7179], [30.6942, 76.8606], [28.6315, 77.2167], [28.495, 77.089], [12.9352, 77.6245], [19.1197, 72.8468]]

export function setup() {
  const health = http.get(`${BASE}/actuator/health`)
  if (health.error || health.status === 0) {
    throw new Error(`Backend health check failed before login: ${health.error || 'unknown transport error'}. Start the local backend on ${BASE}.`)
  }
  if (health.status < 200 || health.status >= 300) {
    throw new Error(`Backend health check returned HTTP ${health.status}. Start the local backend on ${BASE} and verify its logs.`)
  }
  const r = http.post(`${BASE}/api/v1/auth/login`, JSON.stringify({ email: __ENV.EMAIL, password: __ENV.PASSWORD }), { headers: { 'Content-Type': 'application/json' } })
  if (r.error || r.status === 0) {
    throw new Error(`Login request failed before receiving an HTTP response: ${r.error || 'unknown transport error'}. Check BASE_URL and backend availability.`)
  }
  const token = r.json('accessToken')
  if (r.status !== 200 || !token) {
    throw new Error(`Login failed with HTTP ${r.status}. Check EMAIL/PASSWORD and the backend seed configuration.`)
  }
  check(r, { 'logged in': (x) => x.status === 200 })
  return { token }   // access tokens last 15 min: keep DURATION below that
}
export default function (data) {
  const p = places[Math.floor(Math.random() * places.length)]
  const body = JSON.stringify({ customerName: `Load ${__VU}-${__ITER}`, pickupAddress: 'Load pickup', pickupLat: p[0], pickupLng: p[1], dropAddress: 'Load drop', dropLat: p[0] + 0.01, dropLng: p[1] + 0.01, items: [{ name: 'Parcel', quantity: 1 }], priority: 'NORMAL' })
  const res = http.post(`${BASE}/api/v1/orders`, body, { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${data.token}` } })
  if (res.error || res.status === 0) {
    throw new Error(`Order request lost the local backend connection: ${res.error || 'unknown transport error'}. Stop the test and inspect the backend logs.`)
  }
  createLatency.add(res.timings.duration)
  if (res.status === 429) rateLimited.add(1)
  if (res.status === 401 || res.status === 403) unauthorized.add(1)
  check(res, { 'created': (r) => r.status === 201 })
}