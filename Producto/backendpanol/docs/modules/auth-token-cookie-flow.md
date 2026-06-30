# Flujo Completo de Tokens y Cookies

- Estado del documento: vigente
- Ultima verificacion: 2026-06-30
- Fuente de verdad: `AuthService`, `AuthCookieService`,
  `RefreshSessionJooqRepository`, `AuthJooqRepository`, `SecurityConfig`,
  `TokenRevocationValidator`, `TokenRevocationCleanupWorker`

## Objetivo

Este documento explica el flujo completo de autenticacion web del backend:

- que vive en cookies
- que vive en base de datos
- que valida el backend en cada request
- como funciona el refresh token
- como funciona la revocacion inmediata
- como funciona el cleanup automatico de `token_revocation`
- y como la recuperacion de contrasena corta todas las sesiones activas

La implementacion actual usa un modelo hibrido:

- `access token` JWT firmado por backend
- `refresh token` opaco persistido solo como hash
- cookies HTTP-only como transporte principal
- BD como fuente de verdad para sesiones refresh y revocaciones

## Componentes principales

### Base de datos

Tablas relevantes:

1. `public.user`
- identidad del usuario
- credenciales (`password_hash`)
- rol
- estado de intentos fallidos

2. `public.user_session`
- sesiones refresh activas
- referencia al usuario por `user_id`
- `refresh_token_hash`
- `expires_at`
- `device_info`

3. `public.token_revocation`
- denylist de `jti` de access tokens invalidados antes de su expiracion natural
- referencia al usuario por `user_id`
- `jti`
- `expires_at`
- `created_at`

### Backend

Piezas principales:

1. `AuthService`
- emite access token y refresh token
- rota refresh token
- revoca access tokens por `jti`
- invalida sesiones refresh

2. `AuthCookieService`
- crea cookies de access y refresh
- expira cookies en logout y revocacion
- extrae cookies desde `HttpServletRequest`

3. `CookieOrHeaderBearerTokenResolver`
- resuelve el access token desde cookie o header `Authorization`

4. `TokenRevocationValidator`
- consulta si el `jti` del JWT ya fue revocado

5. `TokenRevocationCleanupWorker`
- elimina revocaciones vencidas de `public.token_revocation`

6. `AuthService` en recuperacion de contrasena
- valida codigo y emite `reset_token` opaco temporal
- invalida todas las sesiones refresh del usuario al completar el reset

### Frontend

Piezas relevantes:

1. `apiClient`
- usa `withCredentials: true`
- ante `401`, intenta `POST /api/v2/auth/refresh`

2. `auth_user`
- snapshot no sensible del usuario
- no contiene JWT ni refresh token

## Modelo de tokens

### Access token

Es un JWT HS256 firmado por el backend.

Contiene, al menos:

- `iss`
- `sub = user.uuid`
- `jti`
- `iat`
- `exp`
- `role`

Importante:

- no se persiste completo en base de datos
- viaja en la cookie `panol_access_token`
- se valida en cada request protegida

### Refresh token

Es un token opaco aleatorio de alta entropia.

Importante:

- no es JWT
- no se guarda en claro en la BD
- solo vive en claro en la cookie `panol_refresh_token`
- en BD se persiste `SHA-256(refreshToken)`

### `jti`

Es el identificador unico de una instancia puntual de access token.

Se usa para:

- invalidar un JWT antes de que expire
- soportar cierre remoto de sesion
- soportar logout idempotente con revocacion inmediata

## Cookies de autenticacion

### Cookie `panol_access_token`

Uso:

- transportar el JWT de acceso

Atributos:

- `HttpOnly=true`
- `SameSite=Lax`
- `Path=/`
- host-only
- `Secure` configurable por entorno

Persistencia:

- si `rememberMe=true`, se emite con `Max-Age` del TTL del access token
- si `rememberMe=false`, queda como cookie de sesion del navegador

### Cookie `panol_refresh_token`

Uso:

- transportar el refresh token opaco

Atributos:

- `HttpOnly=true`
- `SameSite=Lax`
- `Path=/api/v2/auth`
- host-only
- `Secure` configurable por entorno

Persistencia:

- si `rememberMe=true`, se emite con `Max-Age` del TTL del refresh token
- si `rememberMe=false`, queda como cookie de sesion del navegador
  y su fila en `user_session` usa el TTL temporal configurado

### Ejemplo de emision

Ejemplo representativo:

```http
Set-Cookie: panol_access_token=eyJ...; Path=/; HttpOnly; SameSite=Lax
Set-Cookie: panol_refresh_token=QjM...; Path=/api/v2/auth; HttpOnly; SameSite=Lax
```

En entornos desplegados, ademas debe quedar:

```http
Secure
```

### Expiracion de cookies

Cuando se cierra o revoca una sesion actual, el backend expira cookies con:

- mismo nombre
- mismo `Path`
- `Max-Age=0`

Eso permite que el navegador elimine la cookie correcta.

### Variables de entorno relacionadas

- `APP_AUTH_JWT_EXPIRATION_SECONDS`
  - TTL del access token.
  - Default: `3600` segundos.
- `APP_AUTH_REFRESH_EXPIRATION_SECONDS`
  - TTL del refresh token persistente y de la sesion en `user_session`
    cuando `rememberMe=true`.
  - Default: `604800` segundos.
- `APP_AUTH_REFRESH_TEMPORARY_EXPIRATION_SECONDS`
  - TTL server-side de la sesion temporal cuando `rememberMe=false`.
  - Default: `86400` segundos.
- `APP_AUTH_COOKIE_SECURE`
  - controla si las cookies salen con atributo `Secure`.
  - `false` en localhost HTTP.
  - `true` en despliegues HTTPS.
- `APP_AUTH_COOKIE_SAME_SITE`
  - controla el atributo `SameSite` de ambas cookies.
  - default actual: `Lax`.
  - pensado para frontend/backend same-site como `localhost` o subdominios del mismo sitio.

## Fuente de verdad por capa

### Lo que valida localmente el backend

Sin ir a BD:

- firma del JWT
- expiracion del JWT
- integridad del token

### Lo que valida con base de datos

Con BD:

1. request protegida normal
- consulta `token_revocation` por `jti`

2. refresh
- consulta `user_session` por `refresh_token_hash`

3. logout o revocacion de sesion
- inserta `jti` en `token_revocation`
- elimina fila de `user_session`

## Job de cleanup de `token_revocation`

El backend tiene un worker programado para purgar revocaciones expiradas.

Comportamiento por default:

- espera `5` minutos despues del arranque
- default de codigo: corre cada `30` minutos
- override local del repo: corre cada `1` dia
- borra hasta `500` filas expiradas por corrida

Variables:

- `APP_AUTH_TOKEN_REVOCATION_CLEANUP_ENABLED=true`
- `APP_AUTH_TOKEN_REVOCATION_CLEANUP_INITIAL_DELAY_MS=300000`
- `APP_AUTH_TOKEN_REVOCATION_CLEANUP_DELAY_MS=86400000`
- `APP_AUTH_TOKEN_REVOCATION_CLEANUP_BATCH_SIZE=500`

El criterio de borrado es simple:

- si `token_revocation.expires_at < now()`, esa fila ya no aporta seguridad porque el JWT ya expiro por si solo
- por eso el worker la puede eliminar

## Flujo completo

### 1. Login

1. El cliente llama `POST /api/v2/auth/login`.
2. El backend valida RUT y password contra `public.user`.
3. Si las credenciales son validas:
   - genera access JWT con `jti`
   - genera refresh token opaco
   - calcula `SHA-256(refreshToken)`
4. Inserta una fila en `public.user_session`.
   - si `rememberMe=true`, usa el TTL persistente
   - si `rememberMe=false`, usa el TTL temporal
5. Responde:
   - body con `role`, `expiresInSeconds` y `user`
   - cookie `panol_access_token`
   - cookie `panol_refresh_token`

### 2. Request autenticada normal

1. El navegador envia `panol_access_token`.
2. `CookieOrHeaderBearerTokenResolver` toma ese token.
3. Spring Security valida:
   - firma
   - `exp`
4. `TokenRevocationValidator` consulta `token_revocation` por `jti`.
5. Si el `jti` no esta revocado, el request sigue autenticado.

### 3. Refresh

1. El frontend recibe `401`.
2. Llama `POST /api/v2/auth/refresh`.
3. El backend lee `panol_refresh_token`.
4. Calcula `SHA-256(refreshToken)`.
5. Busca la fila correspondiente en `public.user_session`.
6. Si existe y no expiro:
   - genera nuevo access token con nuevo `jti`
   - genera nuevo refresh token
   - actualiza la misma fila:
     - `refresh_token_hash`
     - `expires_at`
     - `device_info.currentAccessJti`
     - `device_info.currentAccessExpiresAt`
   - conserva el mismo tipo de sesion:
     - persistente -> TTL persistente
     - temporal -> TTL temporal
7. Responde `204` con cookies nuevas.

### 4. Logout de la sesion actual

1. El backend intenta decodificar el access token actual.
2. Si el JWT es valido:
   - extrae `jti`
   - inserta ese `jti` en `public.token_revocation`
3. Luego lee el refresh token actual.
4. Si existe:
   - calcula su hash
   - elimina solo esa fila de `public.user_session`
5. Expira ambas cookies.
6. Responde `204`.

### 5. Revocacion remota desde sesiones activas

1. El usuario autenticado llama `DELETE /api/v2/auth/me/sessions/{sessionId}`.
2. El backend busca esa sesion por `sessionId + userUuid`.
3. Lee `currentAccessJti` desde `device_info`.
4. Inserta ese `jti` en `public.token_revocation`.
5. Elimina la fila de `public.user_session`.
6. Si esa sesion era la actual, expira tambien cookies.

### 6. Cleanup de revocaciones expiradas

1. Un job backend corre por scheduler.
2. Busca filas de `public.token_revocation` con:

```sql
expires_at < now()
```

3. Borra un lote acotado.
4. Si elimino filas, registra log.
5. Si falla, registra error y no interrumpe la app.

Importante:

- este cleanup aplica solo a `token_revocation`
- no elimina `user_session`

### 7. Reset de contrasena por recuperacion

1. El usuario valida un codigo de recuperacion fuera del flujo de cookies.
2. Cuando completa `POST /api/v2/auth/password-recovery/reset`, el backend:
   - cambia `password_hash`
   - elimina todas las filas activas de `public.user_session` para ese usuario
   - revoca cada `currentAccessJti` encontrado en `device_info`
3. Resultado operativo:
   - cualquier navegador autenticado del mismo usuario cae en la siguiente
     request protegida
   - el refresh silencioso tambien falla porque ya no queda sesion refresh
     valida en base de datos

## Ejemplos de datos

### Ejemplo de fila en `public.user_session`

Campos conceptuales:

```text
id = 41
user_id = 7
refresh_token_hash = 1f4c...a92e
expires_at = 2026-06-20T12:00:00Z
```

`device_info`:

```json
{
  "persistentLogin": true,
  "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/137.0.0.0",
  "currentAccessJti": "9d1f8f5a-75d8-4dbb-9e8a-5f64e2f5f9f3",
  "currentAccessExpiresAt": "2026-06-13T13:00:00Z"
}
```

### Ejemplo de fila en `public.token_revocation`

```text
id = 103
user_id = 7
jti = 9d1f8f5a-75d8-4dbb-9e8a-5f64e2f5f9f3
expires_at = 2026-06-13T13:00:00Z
created_at = 2026-06-13T12:35:00Z
```

Interpretacion:

- ese access token fue invalidado antes de que venciera solo
- cualquier request futura con ese `jti` debe responder `401`
- una vez pasado `expires_at`, la fila ya no aporta seguridad y puede borrarse

## Que consulta la BD en cada escenario

### Request protegida

Consulta:

- `token_revocation` por `jti`

No consulta:

- `user_session`
- `refresh_token_hash`

### Refresh

Consulta:

- `user_session` por `refresh_token_hash`
- `user` por `uuid` para reemitir identidad y rol

### Logout o revocacion remota

Consulta / escritura:

- insercion en `token_revocation`
- eliminacion en `user_session`

## Por que una sesion remota cae casi de inmediato

Cuando una sesion se revoca remotamente:

1. se elimina su refresh token de `user_session`
2. se inserta su `currentAccessJti` en `token_revocation`

Entonces, en la siguiente request de ese navegador:

1. el backend valida el JWT
2. consulta `token_revocation`
3. detecta el `jti` revocado
4. responde `401`
5. el frontend intenta refresh
6. refresh falla porque la fila de `user_session` ya no existe
7. el frontend limpia sesion y redirige a login

No se desloguea por arte de magia entre requests; cae en la siguiente llamada al
backend.

## Job de cleanup

### Objetivo

Evitar que `public.token_revocation` crezca indefinidamente con tokens que ya
igual expiraron por tiempo.

### Configuracion

Variables runtime:

- `APP_AUTH_TOKEN_REVOCATION_CLEANUP_ENABLED`
- `APP_AUTH_TOKEN_REVOCATION_CLEANUP_INITIAL_DELAY_MS`
- `APP_AUTH_TOKEN_REVOCATION_CLEANUP_DELAY_MS`
- `APP_AUTH_TOKEN_REVOCATION_CLEANUP_BATCH_SIZE`

Defaults actuales:

- `enabled=true`
- `initialDelay=300000`
- `delay=1800000`
- `batchSize=500`

### Estrategia

- una corrida = un lote
- sin loop infinito por ejecucion
- ordenado por `expires_at asc, id asc`
- soportado por indice `idx_token_revocation_expires_at`

## Tradeoff del modelo actual

Ventajas:

- el access token no se guarda en `localStorage`
- el refresh token no se guarda en claro en BD
- se puede revocar una sesion de inmediato
- logout y cierre remoto son efectivos sin esperar 1 hora

Costo:

- cada request protegida agrega lookup por `jti` en `token_revocation`

Ese costo es aceptable porque:

- la validacion principal del JWT sigue siendo local
- el lookup por `jti` usa indice unico
- la tabla de revocaciones se mantiene acotada por cleanup

## Resumen operativo

- `panol_access_token` autentica requests protegidas
- `panol_refresh_token` solo sirve para reemitir sesion
- `user_session` es la fuente de verdad de sesiones refresh
- `token_revocation` es la fuente de verdad de access tokens invalidados antes
  de expirar
- el backend valida `jti` en cada request protegida
- el cleanup automatico elimina revocaciones ya vencidas
