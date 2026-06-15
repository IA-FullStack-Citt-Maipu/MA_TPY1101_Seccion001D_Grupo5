import http from 'k6/http';
import { check } from 'k6';

import coordinadorFlow from '../flows/coordinador.js';
import directorFlow from '../flows/director.js';
import docenteFlow from '../flows/docente.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:18080';

export const options = {
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<5000'],
  },
  scenarios: {
    docentes: {
      executor: 'ramping-vus',
      stages: [
        { duration: '2m', target: 12 },
        { duration: '26m', target: 12 },
        { duration: '2m', target: 0 },
      ],
      env: {
        ROLE: 'docente',
      },
    },
    coordinadores: {
      executor: 'ramping-vus',
      stages: [
        { duration: '2m', target: 6 },
        { duration: '26m', target: 6 },
        { duration: '2m', target: 0 },
      ],
      env: {
        ROLE: 'coordinador',
      },
    },
    directores: {
      executor: 'ramping-vus',
      stages: [
        { duration: '2m', target: 2 },
        { duration: '26m', target: 2 },
        { duration: '2m', target: 0 },
      ],
      env: {
        ROLE: 'director',
      },
    },
  },
};

function getCookieValue(response, name) {
  const cookies = response.cookies[name];
  if (!cookies || cookies.length === 0 || !cookies[0].value) {
    return null;
  }
  return cookies[0].value;
}

function requireEnv(name) {
  const value = __ENV[name];
  if (!value) {
    throw new Error(`${name} must be set`);
  }
  return value;
}

function login(baseUrl, role, rut, password) {
  const response = http.post(
    `${baseUrl}/api/v2/auth/login`,
    JSON.stringify({
      rut,
      password,
    }),
    {
      headers: {
        'Content-Type': 'application/json',
      },
    }
  );

  const loginOk = check(response, {
    [`${role} login status is 200`]: (res) => res.status === 200,
  });

  if (!loginOk) {
    throw new Error(`${role} login failed with status ${response.status}: ${response.body}`);
  }

  const accessToken = getCookieValue(response, 'panol_access_token');
  const refreshToken = getCookieValue(response, 'panol_refresh_token');

  if (!accessToken || !refreshToken) {
    throw new Error(`${role} login succeeded but required auth cookies were not returned`);
  }

  return { accessToken, refreshToken };
}

function logout(baseUrl, accessToken, refreshToken) {
  http.post(`${baseUrl}/api/v2/auth/logout`, null, {
    headers: {
      Cookie: `panol_access_token=${accessToken}; panol_refresh_token=${refreshToken}`,
    },
  });
}

export function setup() {
  const docenteSession = login(
    BASE_URL,
    'docente',
    requireEnv('DOCENTE_RUT'),
    requireEnv('DOCENTE_PASSWORD')
  );
  const coordinadorSession = login(
    BASE_URL,
    'coordinador',
    requireEnv('COORD_RUT'),
    requireEnv('COORD_PASSWORD')
  );
  const directorSession = login(
    BASE_URL,
    'director',
    requireEnv('DIRECTOR_RUT'),
    requireEnv('DIRECTOR_PASSWORD')
  );

  return {
    baseUrl: BASE_URL,
    docenteToken: docenteSession.accessToken,
    docenteRefreshToken: docenteSession.refreshToken,
    coordToken: coordinadorSession.accessToken,
    coordRefreshToken: coordinadorSession.refreshToken,
    directorToken: directorSession.accessToken,
    directorRefreshToken: directorSession.refreshToken,
  };
}

export default function (data) {
  switch (__ENV.ROLE) {
    case 'docente':
      docenteFlow(data.baseUrl, data.docenteToken);
      return;
    case 'coordinador':
      coordinadorFlow(data.baseUrl, data.coordToken);
      return;
    case 'director':
      directorFlow(data.baseUrl, data.directorToken);
      return;
    default:
      throw new Error(`Unsupported ROLE: ${__ENV.ROLE}`);
  }
}

export function teardown(data) {
  logout(data.baseUrl, data.docenteToken, data.docenteRefreshToken);
  logout(data.baseUrl, data.coordToken, data.coordRefreshToken);
  logout(data.baseUrl, data.directorToken, data.directorRefreshToken);
}
