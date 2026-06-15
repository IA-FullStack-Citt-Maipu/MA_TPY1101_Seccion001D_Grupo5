import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = 'http://localhost:18080';

export const options = {
  vus: 3,
  duration: '1m',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<5000'],
  },
};

function getCookieValue(response, name) {
  const cookies = response.cookies[name];
  if (!cookies || cookies.length === 0 || !cookies[0].value) {
    return null;
  }
  return cookies[0].value;
}

function isValidJson(body) {
  try {
    JSON.parse(body);
    return true;
  } catch (error) {
    return false;
  }
}

export function setup() {
  if (!__ENV.DOCENTE_RUT || !__ENV.DOCENTE_PASSWORD) {
    throw new Error('DOCENTE_RUT and DOCENTE_PASSWORD must be set');
  }

  const response = http.post(
    `${BASE_URL}/api/v2/auth/login`,
    JSON.stringify({
      // RUT must include check digit with hyphen format: XXXXXXXX-D
      // Example: 22222222-2 (not 22222222)
      rut: __ENV.DOCENTE_RUT,
      password: __ENV.DOCENTE_PASSWORD,
    }),
    {
      headers: {
        'Content-Type': 'application/json',
      },
    }
  );

  const loginOk = check(response, {
    'login status is 200': (res) => res.status === 200,
  });

  if (!loginOk) {
    throw new Error(`Login failed with status ${response.status}: ${response.body}`);
  }

  const accessToken = getCookieValue(response, 'panol_access_token');
  const refreshToken = getCookieValue(response, 'panol_refresh_token');

  if (!accessToken || !refreshToken) {
    throw new Error('Login succeeded but required auth cookies were not returned');
  }

  return { accessToken, refreshToken };
}

export default function (data) {
  const authHeaders = {
    Authorization: `Bearer ${data.accessToken}`,
  };

  const implementsResponse = http.get(`${BASE_URL}/api/v2/implements`, {
    headers: authHeaders,
  });
  check(implementsResponse, {
    'implements status is 200': (res) => res.status === 200,
    'implements body is valid JSON': (res) => isValidJson(res.body),
  });
  sleep(1);

  const loansResponse = http.get(`${BASE_URL}/api/v2/loans?mine=true`, {
    headers: authHeaders,
  });
  check(loansResponse, {
    'loans status is 200': (res) => res.status === 200,
  });
  sleep(1);

  const meResponse = http.get(`${BASE_URL}/api/v2/auth/me`, {
    headers: authHeaders,
  });
  check(meResponse, {
    'me status is 200': (res) => res.status === 200,
  });
  sleep(1);
}

export function teardown(data) {
  http.post(`${BASE_URL}/api/v2/auth/logout`, null, {
    headers: {
      Cookie: `panol_access_token=${data.accessToken}; panol_refresh_token=${data.refreshToken}`,
    },
  });
}
