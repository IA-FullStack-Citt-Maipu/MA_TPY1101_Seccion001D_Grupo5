- Estado del documento: vigente
- Ultima verificacion: 2026-06-10
- Fuente de verdad: ver matriz canonica vigente y codigo fuente actual

# Payloads Frontend / Backend

- Ultima actualizacion: 2026-06-10
- Alcance: contratos JSON usados por frontend y backend

## 1) Payload de error publico (backend -> frontend)

Formato uniforme para respuestas de error consumidas por UI:

```json
{
  "code": "AUTH_EMAIL_ALREADY_IN_USE",
  "message": "El correo ya esta en uso",
  "timestamp": "2026-05-07T10:00:00Z"
}
```

Campos:

1. `code` (string)
- Codigo funcional cuando existe (`AUTH_EMAIL_ALREADY_IN_USE`,
  `CATEGORY_NAME_DUPLICATE`, etc.). Si no existe codigo funcional, se usa
  el codigo HTTP como string (`"400"`, `"401"`, `"500"`, etc.).

2. `message` (string)
- Mensaje publico consumible por frontend.

3. `timestamp` (ISO-8601 string)
- Momento de generacion del error.

## 2) Payload de logs internos (warning/error)

Formato estructurado para observabilidad (GCP/log sinks):

```json
{
  "event": "client_error_handled",
  "timestamp": "2026-05-07T10:00:00Z",
  "endpoint_tag": "auth",
  "code": 400,
  "error_type": "HttpMessageNotReadableException",
  "user_uuid": "Ninguno",
  "path": "/api/v2/auth/login",
  "cause": "Detalle tecnico del error"
}
```

Campos:

1. `event` (string)
2. `timestamp` (ISO-8601 string)
3. `endpoint_tag` (string)
4. `code` (number)
5. `error_type` (string)
6. `user_uuid` (string UUID | `"Ninguno"`)
7. `path` (string)
8. `cause` (string)

## 3) Payload de login

### Request (`POST /api/v2/auth/login`)

```json
{
  "rut": "22307980",
  "password": "******",
  "rememberMe": true
}
```

Nota frontend:
- El RUT puede visualizarse formateado (`22.307.980`), pero se envia limpio (`22307980`).

### Response 200

```json
{
  "role": "COORDINADOR",
  "expiresInSeconds": 3600,
  "user": {
    "id": "97b4e089-26ef-456b-9edc-16edbbd67641",
    "name": "Cesar Coordinador de laboratorio",
    "email": "cesar.coordinador.22307980@duocuc.cl",
    "role": "COORDINADOR"
  }
}
```

Notas:
- El JWT de acceso ya no viaja en el body.
- La respuesta setea cookies HTTP-only `panol_access_token` y
  `panol_refresh_token`.

## 4) Payload de logout

### Request (`POST /api/v2/auth/logout`)

- Sin body.
- Usa cookies de auth; no requiere `Authorization: Bearer`.

### Response 204

- Sin body.

## 5) Refresh de sesion

### Request (`POST /api/v2/auth/refresh`)

- Sin body.
- Usa la cookie HTTP-only `panol_refresh_token`.

### Response 204

- Sin body.
- Reemite `panol_access_token` y `panol_refresh_token`.

## 6) Perfil actual del usuario

### Response (`GET /api/v2/auth/me`)

```json
{
  "id": "97b4e089-26ef-456b-9edc-16edbbd67641",
  "name": "Cesar Coordinador de laboratorio",
  "email": "cesar.coordinador.22307980@duocuc.cl",
  "role": "COORDINADOR"
}
```

## 7) Cambio de correo del usuario actual

### Request (`PATCH /api/v2/auth/me/email`)

```json
{
  "email": "nuevo.correo@duocuc.cl"
}
```

### Response 200

Mismo contrato que `GET /api/v2/auth/me`.

Error funcional esperado:

- `AUTH_EMAIL_ALREADY_IN_USE`

## 8) Cambio de contrasena del usuario actual

### Request (`PATCH /api/v2/auth/me/password`)

```json
{
  "current_password": "Panol123",
  "new_password": "Panol1234"
}
```

### Response 204

- Sin body.

Errores funcionales esperados:

- `AUTH_CURRENT_PASSWORD_REQUIRED`
- `AUTH_CURRENT_PASSWORD_INVALID`
- `AUTH_NEW_PASSWORD_REQUIRED`
- `AUTH_NEW_PASSWORD_TOO_SHORT`
- `AUTH_PASSWORD_REUSE_NOT_ALLOWED`

## 9) Sesiones activas del usuario actual

### Response (`GET /api/v2/auth/me/sessions`)

```json
[
  {
    "id": "41",
    "current": true,
    "persistentLogin": true,
    "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/137.0.0.0",
    "createdAt": "2026-06-13T18:20:00Z",
    "expiresAt": "2026-06-20T18:20:00Z"
  }
]
```

Notas:
- `id` es string por contrato, aunque internamente provenga de `bigint`.
- `userAgent` puede venir `null`.
- Nunca se exponen `refresh_token_hash` ni `currentAccessJti`.

### Request (`DELETE /api/v2/auth/me/sessions/{sessionId}`)

- Sin body.
- Requiere autenticacion por cookie `panol_access_token`.

### Response 204

- Sin body.

Error funcional esperado:

- `404` cuando la sesion no existe o no pertenece al usuario autenticado.

## 10) Consumo en frontend

- `src/services/apiClient.ts` espera en errores: `code`, `message`, `timestamp`.
- `src/services/authService.ts` consume `POST /api/v2/auth/login` y persiste
  solo `auth_user` como snapshot no sensible.
- `src/services/apiClient.ts` trabaja con `withCredentials: true` y hace
  refresh silencioso contra `POST /api/v2/auth/refresh` cuando recibe `401`.
- `src/services/profileService.ts` consume `GET /api/v2/auth/me`,
  `GET /api/v2/auth/me/sessions`, `DELETE /api/v2/auth/me/sessions/{sessionId}`,
  `PATCH /api/v2/auth/me/email` y `PATCH /api/v2/auth/me/password`.
- Cualquier otro detalle tecnico debe permanecer en logs internos del backend.
