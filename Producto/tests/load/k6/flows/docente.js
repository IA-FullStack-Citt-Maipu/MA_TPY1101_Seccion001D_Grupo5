import http from 'k6/http';
import { check, sleep } from 'k6';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

export default function (baseUrl, accessToken) {
  const authHeaders = {
    Authorization: `Bearer ${accessToken}`,
  };

  const implementsResponse = http.get(`${baseUrl}/api/v2/implements`, {
    headers: authHeaders,
  });
  check(implementsResponse, {
    'docente implements status is 200': (res) => res.status === 200,
  });
  sleep(randomIntBetween(1, 3));

  const loansResponse = http.get(`${baseUrl}/api/v2/loans?mine=true&page=1&size=10`, {
    headers: authHeaders,
  });
  check(loansResponse, {
    'docente loans status is 200': (res) => res.status === 200,
  });
  sleep(randomIntBetween(1, 2));

  const meResponse = http.get(`${baseUrl}/api/v2/auth/me`, {
    headers: authHeaders,
  });
  check(meResponse, {
    'docente me status is 200': (res) => res.status === 200,
  });
  sleep(1);
}
