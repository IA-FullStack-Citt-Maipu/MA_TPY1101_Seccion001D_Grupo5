- Estado del documento: vigente
- Ultima verificacion: 2026-06-28
- Fuente de verdad: ver matriz canonica vigente y codigo fuente actual

# Payloads Frontend / Backend

- Ultima actualizacion: 2026-06-28
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
  "rut": "223079801",
  "password": "******",
  "rememberMe": true
}
```

Nota frontend:
- El RUT puede visualizarse formateado (`22.307.980-1`), pero se envia limpio y completo con DV (`223079801`).

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
- El token puente es exclusivo del flujo `AI-Agent`: no debe reutilizarse como
  token general del backend.
- Cuando `token_use = bot-panol`, el backend solo lo acepta con
  `X-Client-Origin: AI-Agent`, `X-Client-Secret` valido y metodos de solo lectura
  (`GET`, `HEAD`, `OPTIONS`).

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
- `POST /api/v1/chat` puede responder `ui_blocks` opcional para que el frontend
  renderice listas y metricas enriquecidas sin depender solo de Markdown.
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
- La auto-reserva no descuenta stock fisico al crear o editar.
- Para `reusable` e `individual`, la disponibilidad sigue evaluandose por traslape del rango solicitado.
- Para `consumable`, la disponibilidad reservada queda bloqueada globalmente mientras el prestamo siga en `approved` o `prepared`.
- El docente solo recibe notificacion cuando el prestamo pasa a `prepared`, no cuando entra en `approved`.
- `PATCH /api/v2/loans/{loanUuid}` reutiliza el payload de creacion y solo se permite mientras el prestamo siga en `approved`.
- `PATCH /api/v2/loans/{loanUuid}/cancel` mantiene cancelacion operativa para reservas/preparaciones activas.
- `POST /api/v2/loans/{loanUuid}/prepare` separa fisicamente implementos y hoy solo valida estado `approved`.
- `POST /api/v2/loans/{loanUuid}/delivery` solo acepta prestamos `prepared`.
- La entrega sigue siendo el punto flexible para variar cantidades, reseleccionar `asset_codes` y agregar implementos extra.
- Los items `consumable` se cierran en la entrega. Si el prestamo entregado no deja retornables pendientes, se auto-finaliza en `completed`.
- `GET /api/v2/loans/{loanUuid}/return-context` expone los items pendientes de devolucion y, para `individual`, las unidades entregadas aun abiertas.
- `POST /api/v2/loans/{loanUuid}/return` procesa devolucion con variaciones.
- `POST /api/v2/loans/{loanUuid}/complete` cierra todos los retornables pendientes como retorno correcto.

### Query de listado (`GET /api/v2/loans`)

- `page` y `size` mantienen la paginacion actual.
- `mine=true|false` mantiene el filtro por visibilidad del solicitante segun rol.
- `from` y `to` son opcionales, pero deben enviarse juntos si se usan.
- `from` es inclusivo y `to` es exclusivo sobre `scheduled_at`.
- Cuando `from`/`to` no se envian, el endpoint conserva el comportamiento historico del listado general.
- La agenda mensual consume este filtro por rango visible para evitar traer todo el universo de prestamos.

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
      "item_type": "individual",
      "requested_quantity": 2,
      "reserved_quantity": 2,
      "delivered_quantity": 0,
      "returned_quantity": 0
    }
  ]
}
```

Notas:

- `item_type` usa literales de backend: `consumable`, `reusable`, `individual`.
- `requested_quantity` sigue siendo la referencia original del docente.
- `reserved_quantity` refleja la reserva logica del prestamo.
- `delivered_quantity` refleja lo efectivamente entregado.
- `returned_quantity` refleja solo retorno util/bueno.
- En prestamos activos, `reserved_quantity` representa reserva operativa vigente.
- En prestamos `cancelled` o `expired` sin entrega, `reserved_quantity` puede mantenerse con valor historico aunque la reserva ya se haya liberado.
- Cierres por dano, perdida, descarte o consumo parcial se registran en el detalle interno del prestamo, pero no se exponen en este payload resumen.

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

Payload base:

```json
{
  "items": [
    {
      "implement_uuid": "0de53ed2-6ce7-4376-88ec-b743d5e683b9",
      "quantity": 2,
      "asset_codes": ["IND-0001", "IND-0002"]
    }
  ],
  "notes": "Entrega realizada en sala"
}
```

### Response de contexto de devolucion (`GET /api/v2/loans/{loanUuid}/return-context`)

```json
{
  "loan_uuid": "2f0f2a7d-9d89-4d75-b2c1-b9d88b8b4f4f",
  "items": [
    {
      "implement_uuid": "0de53ed2-6ce7-4376-88ec-b743d5e683b9",
      "implement_name": "Fonendoscopio",
      "item_type": "individual",
      "delivered_quantity": 2,
      "pending_return_quantity": 2,
      "individuals": [
        {
          "individual_uuid": "5e829c37-48f0-4cc0-9bc2-652e240a1a64",
          "asset_code": "IND-0001"
        },
        {
          "individual_uuid": "bcbc7f6b-9da1-4630-bb81-4ebbb0ccf08f",
          "asset_code": "IND-0002"
        }
      ]
    }
  ]
}
```

Notas:

- `pending_return_quantity` se calcula sobre lo entregado menos lo ya cerrado.
- El arreglo `individuals` solo se llena para items `individual`.
- Los `consumable` ya cerrados en entrega no aparecen como pendientes de devolucion.

### Request de devolucion con variacion (`POST /api/v2/loans/{loanUuid}/return`)

```json
{
  "returned_individuals": [
    {
      "individual_uuid": "5e829c37-48f0-4cc0-9bc2-652e240a1a64",
      "return_condition": "good"
    },
    {
      "individual_uuid": "bcbc7f6b-9da1-4630-bb81-4ebbb0ccf08f",
      "return_condition": "damaged"
    }
  ],
  "consumable_returns": [
    {
      "implement_uuid": "0fb49c0a-04fd-4d83-9c71-fd3e66385b60",
      "quantity": 3
    }
  ],
  "notes": "Se devolvieron 3 unidades reutilizables y 1 activo quedo danado"
}
```

Notas:

- `returned_individuals` aplica a items `individual`.
- `return_condition` acepta `good`, `damaged`, `lost` o `discarded`.
- `consumable_returns` es un nombre historico del contrato, pero hoy aplica a items `reusable`.
- Para `reusable`, `quantity` indica cuanto vuelve en buen estado; la diferencia contra lo pendiente se cierra como consumo.
- Si no se manda ningun retorno y el prestamo todavia tiene retornables pendientes, el backend responde error.

### Request de cierre directo (`POST /api/v2/loans/{loanUuid}/complete`)

```json
{
  "notes": "Todo retornado correctamente"
}
```

Notas:

- `complete` asume retorno correcto para todos los items retornables pendientes.
- `return` debe usarse cuando hace falta clasificar unidades `individual` o informar retorno parcial de `reusable`.
