// k6 load test for order creation (the write path: API -> Postgres + outbox -> Kafka -> dispatch).
// Usage: k6 run -e BASE_URL=http://localhost:8080 -e EMAIL=demo@fleetflow.dev -e PASSWORD=... -e RATE=100 create-orders.js
// Run three times with RATE=100, 500, 1000. Start the backend with RATE_LIMIT_ORDERS=1000000 or the per-user limiter will (correctly) return 429.
// No thresholds are asserted on purpose: record what you measure in the README table.
import http from 'k6/http'
import { check } from 'k6'
import { Trend } from 'k6/metrics'

const RATE = Number(__ENV.RATE || 100)
export const options = {
  scenarios: { orders: { executor: 'constant-arrival-rate', rate: RATE, timeUnit: '1s', duration: __ENV.DURATION || '60s', preAllocatedVUs: Math.max(50, RATE), maxVUs: RATE * 4 } },
}
const createLatency = new Trend('order_create_latency', true)
const BASE = __ENV.BASE_URL || 'http://localhost:8080'
const places = [[30.7398, 76.7827], [30.7046, 76.7179], [30.6942, 76.8606], [28.6315, 77.2167], [28.495, 77.089], [12.9352, 77.6245], [19.1197, 72.8468]]

export function setup() {
  const r = http.post(`${BASE}/api/v1/auth/login`, JSON.stringify({ email: __ENV.EMAIL, password: __ENV.PASSWORD }), { headers: { 'Content-Type': 'application/json' } })
  check(r, { 'logged in': (x) => x.status === 200 })
  return { token: r.json('accessToken') }   // access tokens last 15 min: keep DURATION below that
}
export default function (data) {
  const p = places[Math.floor(Math.random() * places.length)]
  const body = JSON.stringify({ customerName: `Load ${__VU}-${__ITER}`, pickupAddress: 'Load pickup', pickupLat: p[0], pickupLng: p[1], dropAddress: 'Load drop', dropLat: p[0] + 0.01, dropLng: p[1] + 0.01, items: [{ name: 'Parcel', quantity: 1 }], priority: 'NORMAL' })
  const res = http.post(`${BASE}/api/v1/orders`, body, { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${data.token}` } })
  createLatency.add(res.timings.duration)
  check(res, { 'created': (r) => r.status === 201 })
}
