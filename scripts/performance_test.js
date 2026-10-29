import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Custom Metrics
const errorRate = new Rate('error_rate');
const stationNearbyDuration = new Trend('station_nearby_duration');
const parcelCreationDuration = new Trend('parcel_creation_duration');
const terminalStatusDuration = new Trend('terminal_status_duration');
const terminalPickupDuration = new Trend('terminal_pickup_duration');

export const options = {
  scenarios: {
    // 1. Nominal Load: 20 VUs querying stations and terminal status for 20 seconds
    nominal_load: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: '5s', target: 20 },
        { duration: '10s', target: 20 },
        { duration: '5s', target: 0 },
      ],
      gracefulRampDown: '2s',
      exec: 'nominalWorkflow',
    },

    // 2. Black Friday Spike: Sudden burst of parcel reservations
    black_friday_spike: {
      executor: 'ramping-vus',
      startTime: '22s',
      startVUs: 5,
      stages: [
        { duration: '3s', target: 60 },
        { duration: '10s', target: 60 },
        { duration: '4s', target: 0 },
      ],
      gracefulRampDown: '2s',
      exec: 'parcelReservationWorkflow',
    },

    // 3. Security Brute-Force Defense: High-speed PIN entry testing 60s lockout (HTTP 429)
    security_lockout_test: {
      executor: 'per-vu-iterations',
      startTime: '42s',
      vus: 5,
      iterations: 6,
      maxDuration: '10s',
      exec: 'securityLockoutWorkflow',
    },
  },

  thresholds: {
    // Overall error rate should be strictly low for normal flows
    'http_req_duration': ['p(95)<250', 'p(99)<500'],
    'station_nearby_duration': ['p(95)<150'],
    'parcel_creation_duration': ['p(95)<300'],
  },
};

const BASE_URL = __ENV.TARGET_URL || 'http://localhost:8080';

// Workflow 1: Customer searching nearby lockers & terminal health
export function nominalWorkflow() {
  const nearbyRes = http.get(`${BASE_URL}/api/v1/stations/nearby?lat=40.6405&lon=-8.6538&maxDistanceKm=10`);
  stationNearbyDuration.add(nearbyRes.timings.duration);
  const nearbyOk = check(nearbyRes, {
    'nearby stations status is 200': (r) => r.status === 200,
    'nearby stations returns list': (r) => {
      try {
        const body = JSON.parse(r.body);
        return Array.isArray(body);
      } catch (e) {
        return false;
      }
    },
  });
  errorRate.add(!nearbyOk);

  const statusRes = http.get(`${BASE_URL}/api/v1/terminals/STATION-AVEIRO-01/status`);
  terminalStatusDuration.add(statusRes.timings.duration);
  const statusOk = check(statusRes, {
    'terminal status is 200': (r) => r.status === 200,
  });
  errorRate.add(!statusOk);

  sleep(0.5);
}

// Workflow 2: E-Commerce partner booking compartments (under spike load)
export function parcelReservationWorkflow() {
  const payload = JSON.stringify({
    dimension: 'S',
    recipientPhone: '+351912345678',
    recipientEmail: `k6_${__VU}_${__ITER}@example.com`,
    stationCode: 'STATION-AVEIRO-01',
  });

  const params = {
    headers: { 'Content-Type': 'application/json' },
  };

  const res = http.post(`${BASE_URL}/api/v1/parcels`, payload, params);
  parcelCreationDuration.add(res.timings.duration);

  // Status can be 201 Created or 409/422 if all S/M compartments are booked out
  const validResponse = check(res, {
    'reservation returns expected status (201 or 409/422)': (r) =>
      r.status === 201 || r.status === 409 || r.status === 422,
    'no unhandled internal server error (500)': (r) => r.status !== 500,
  });
  errorRate.add(!validResponse);

  sleep(0.2);
}

// Workflow 3: Automated brute-force attacker testing security lockout
export function securityLockoutWorkflow() {
  const payload = JSON.stringify({
    pin: '000000',
  });

  const params = {
    headers: { 'Content-Type': 'application/json' },
  };

  const res = http.post(`${BASE_URL}/api/v1/terminals/STATION-AVEIRO-01/pickup`, payload, params);
  terminalPickupDuration.add(res.timings.duration);

  // Status must be 401 Unauthorized or 429 Too Many Requests (Lockout)
  check(res, {
    'invalid pin returns 401 or 429': (r) => r.status === 401 || r.status === 429,
  });

  sleep(0.1);
}
