- Estado del documento: vigente
- Ultima verificacion: 2026-06-10
- Fuente de verdad: ver matriz canonica vigente y codigo fuente actual

# Payloads Frontend / Backend

- Ultima actualizacion: 2026-06-26
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

## 7) Token puente para `bot-panol`

### Request (`POST /api/v2/auth/me/bot-token`)

- Sin body.
- Requiere autenticacion por cookie `panol_access_token`.
- Solo disponible para roles `COORDINADOR` y `DIRECTOR`.

### Response 200

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "expiresInSeconds": 300
}
```

Notas:
- El token se usa solo para llamar `POST /api/v1/chat` del microservicio
  `bot-panol`.
- El frontend no debe persistirlo en `localStorage` ni `sessionStorage`.
- El token incluye `aud = bot-panol` y no reemplaza al access token web basado
  en cookies.

## 8) Cambio de correo del usuario actual

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

## 9) Cambio de contrasena del usuario actual

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

## 10) Sesiones activas del usuario actual

### Response (`GET /api/v2/auth/me/sessions`)

```json
[
  {
    "id": "41",
    "current": true,
    "persistentLogin": true,
    "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/137.0.0.0",
    "createdAt": "2026-06-13T18:20:00Z",
    "accessExpiresAt": "2026-06-13T19:20:00Z",
    "sessionExpiresAt": "2026-06-20T18:20:00Z"
  }
]
```

Notas:
- `id` es string por contrato, aunque internamente provenga de `bigint`.
- `userAgent` puede venir `null`.
- `accessExpiresAt` representa el vencimiento del access token vigente para esa
  sesion.
- `sessionExpiresAt` representa el vencimiento total de la sesion refresh
  almacenada en backend.
- Nunca se exponen `refresh_token_hash` ni `currentAccessJti`.

### Request (`DELETE /api/v2/auth/me/sessions/{sessionId}`)

- Sin body.
- Requiere autenticacion por cookie `panol_access_token`.

### Response 204

- Sin body.

Error funcional esperado:

- `404` cuando la sesion no existe o no pertenece al usuario autenticado.

## 11) Consumo en frontend

- `src/services/apiClient.ts` espera en errores: `code`, `message`, `timestamp`.
- `src/services/authService.ts` consume `POST /api/v2/auth/login` y persiste
  solo `auth_user` como snapshot no sensible.
- `src/services/botService.ts` solicita `POST /api/v2/auth/me/bot-token` usando
  la sesion por cookies y luego llama `POST /api/v1/chat` con
  `Authorization: Bearer <token-puente>`.
- `src/services/apiClient.ts` trabaja con `withCredentials: true` y hace
  refresh silencioso contra `POST /api/v2/auth/refresh` cuando recibe `401`.
- `src/services/profileService.ts` consume `GET /api/v2/auth/me`,
  `GET /api/v2/auth/me/sessions`, `DELETE /api/v2/auth/me/sessions/{sessionId}`,
  `PATCH /api/v2/auth/me/email` y `PATCH /api/v2/auth/me/password`.
- Cualquier otro detalle tecnico debe permanecer en logs internos del backend.

## 12) Prestamos v2

### Flujo vigente

- `POST /api/v2/loans` crea la solicitud y auto-reserva el detalle completo en la misma transaccion.
- Los prestamos nuevos nacen en `approved` como estado tecnico de reserva, pero en UI deben leerse como "Reservado".
- El docente solo recibe notificacion cuando el prestamo pasa a `prepared`, no cuando entra en `approved`.
- `POST /api/v2/loans/{loanUuid}/prepare` separa fisicamente implementos desde 60 minutos antes de `scheduled_at`.
- `POST /api/v2/loans/{loanUuid}/delivery` solo acepta prestamos `prepared` y desde `scheduled_at`.
- `PATCH /api/v2/loans/{loanUuid}/review` queda solo para compatibilidad con prestamos legacy en `pending`.

### Response resumen/detalle (`GET /api/v2/loans`, `GET /api/v2/loans/{loanUuid}`, `POST /api/v2/loans`)

```json
{
  "uuid": "2f0f2a7d-9d89-4d75-b2c1-b9d88b8b4f4f",
  "requester_uuid": "e16c39c1-94f4-461f-9c35-9488f4061f78",
  "status": "approved",
  "scheduled_at": "2026-06-26T15:00:00-04:00",
  "expected_return_at": "2026-06-26T17:00:00-04:00",
  "created_at": "2026-06-26T10:00:00-04:00",
  "completed_at": null,
  "room": {
    "uuid": "88ce8ad0-6575-45eb-965a-06b223fc5cf4",
    "name": "Sala 301"
  },
  "subject": null,
  "items": [
    {
      "implement_uuid": "0de53ed2-6ce7-4376-88ec-b743d5e683b9",
      "implement_name": "Fonendoscopio",
      "requested_quantity": 2,
      "reserved_quantity": 2,
      "delivered_quantity": 0,
      "returned_quantity": 0
    }
  ]
}
```

Notas:

- `requested_quantity` sigue siendo la referencia original del docente.
- `reserved_quantity` refleja la reserva logica vigente.
- `delivered_quantity` refleja lo efectivamente entregado.
- `returned_quantity` refleja lo efectivamente devuelto/cerrado.

### Request de preparacion (`POST /api/v2/loans/{loanUuid}/prepare`)

```json
{
  "notes": "Preparado en panol principal"
}
```

### Request de entrega (`POST /api/v2/loans/{loanUuid}/delivery`)

- Mantiene la flexibilidad vigente para:
  - entrega parcial
  - cambio de cantidades
  - seleccion/reseleccion de `asset_codes`
  - implementos adicionales
