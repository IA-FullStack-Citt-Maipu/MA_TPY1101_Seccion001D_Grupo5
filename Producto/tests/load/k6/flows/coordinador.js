import http from 'k6/http';
import { check, sleep } from 'k6';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

export default function (baseUrl, accessToken) {
  const authHeaders = {
    Authorization: `Bearer ${accessToken}`,
  };

  const loansResponse = http.get(`${baseUrl}/api/v2/loans?page=1&size=10`, {
    headers: authHeaders,
  });
  check(loansResponse, {
    'coordinador loans status is 200': (res) => res.status === 200,
  });
  sleep(randomIntBetween(1, 3));

  const implementsResponse = http.get(`${baseUrl}/api/v2/implements`, {
    headers: authHeaders,
  });
  check(implementsResponse, {
    'coordinador implements status is 200': (res) => res.status === 200,
  });
  sleep(randomIntBetween(1, 2));

  const categoriesResponse = http.get(`${baseUrl}/api/v2/categories/active`, {
    headers: authHeaders,
  });
  check(categoriesResponse, {
    'coordinador categories status is 200': (res) => res.status === 200,
  });
  sleep(1);
}
