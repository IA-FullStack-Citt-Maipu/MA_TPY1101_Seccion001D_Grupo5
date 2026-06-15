# Modulo: auth

- Estado del documento: vigente
- Ultima verificacion: 2026-06-13
- Fuente de verdad: `AuthV2Controller`, `AuthService`, `AuthCookieService`,
  `RefreshSessionJooqRepository`, `SecurityConfig`, `TokenRevocationValidator`

Ver tambien:

- [Flujo completo de tokens y cookies](./auth-token-cookie-flow.md)

## Responsabilidad

Autenticacion con JWT en cookie HTTP-only, refresh token opaco persistido en
`public.user_session`, bloqueo temporal por intentos fallidos, revocacion de
token en logout y autogestion del usuario autenticado (`/me`, sesiones activas,
cambio de correo y cambio de contrasena).

## API vigente

Base path: `/api/v2/auth`

- `POST /login`
- `POST /refresh`
- `POST /logout`
- `GET /me`
- `GET /me/sessions`
- `DELETE /me/sessions/{sessionId}`
- `PATCH /me/email`
- `PATCH /me/password`

## Reglas clave

- Login, refresh y logout son `permitAll`; el backend autentica `/me` y el resto
  de endpoints protegidos desde la cookie `panol_access_token`.
- Login setea `panol_access_token` y `panol_refresh_token` como cookies
  HTTP-only.
- Refresh rota el refresh token y reemite ambas cookies.
- Login devuelve `role`, `expiresInSeconds` y `user` para bootstrap de sesion
  del frontend; el JWT ya no se expone en el body.
- `GET /me/sessions` devuelve solo las sesiones del usuario autenticado.
- `DELETE /me/sessions/{sessionId}` revoca una sesion puntual del mismo usuario,
  revoca su `currentAccessJti` cuando existe y elimina la fila refresh.
- `PATCH /me/email` normaliza el correo a lowercase y rechaza duplicados.
- `PATCH /me/password` exige contrasena actual valida, minimo 8 caracteres y
  no permite reutilizar la misma contrasena.
- Logout es idempotente: revoca el access token vigente por `jti` cuando aplica,
  elimina la sesion refresh actual y expira ambas cookies.
- La validacion de request autenticada consulta `token_revocation` por `jti`
  en cada request protegida para cortar access tokens revocados de inmediato.
- Las revocaciones cuyo `expires_at` ya paso se purgan automaticamente por job
  backend para evitar crecimiento indefinido de `token_revocation`.
- Auditoria via puerto de dominio (`AuditLogPort`).
- Eventos de auth y usuario via outbox cuando aplica (`UserLoggedIn`,
  `UserLoggedOut`, `UserEmailChanged`, `UserPasswordChanged`, `LoginFailed`).

## Modelo de identificadores

### Identificadores del usuario

- `user.id`
  - `bigint` interno de base de datos.
  - Se usa como foreign key en tablas como `user_session` y
    `token_revocation`.

- `user.uuid`
  - Identificador estable del usuario a nivel de aplicacion.
  - Se usa como `sub` del JWT de acceso y como identificador expuesto al
    frontend en `/me`.

### Identificadores de sesion y token

- `user_session.id`
  - PK interna de una fila de sesion refresh.
  - Se expone solo para administracion de sesiones propias en
    `GET/DELETE /api/v2/auth/me/sessions`.
  - No funciona como credencial.
  - Solo sirve como puntero interno para rotar o borrar una sesion ya validada.

- `refresh token`
  - Token opaco aleatorio de alta entropia.
  - Solo existe en claro en la cookie `panol_refresh_token`.
  - Es el secreto real de la sesion refresh.

- `refresh_token_hash`
  - SHA-256 del refresh token crudo.
  - Es el valor persistido en `public.user_session`.
  - El backend nunca necesita guardar el refresh token en claro.

- `jti`
  - Identificador unico de una instancia puntual de access token.
  - Permite invalidar un JWT antes de su expiracion natural.

## Tablas involucradas

### `public.user`

- Fuente de verdad de identidad y credenciales.
- Contiene `id`, `uuid`, `rut`, `email`, `password_hash`, rol y estado de
  intentos fallidos.

### `public.user_session`

- Almacena sesiones refresh activas.
- Columnas relevantes:
  - `id`: PK interna de la sesion.
  - `user_id`: FK a `user.id`.
  - `refresh_token_hash`: hash unico del refresh token.
  - `device_info`: metadata best-effort del cliente, serializada como JSON con
    `persistentLogin`, `userAgent`, `currentAccessJti` y
    `currentAccessExpiresAt`.
  - `expires_at`: expiracion de la sesion refresh.
- Restriccion relevante:
  - `UNIQUE (refresh_token_hash)`

### `public.token_revocation`

- Almacena `jti` de access tokens revocados.
- Columnas relevantes:
  - `id`: PK interna.
  - `user_id`: FK a `user.id`.
  - `jti`: identificador unico del JWT revocado.
  - `expires_at`: expiracion original del token revocado.
- Restriccion relevante:
  - `UNIQUE (jti)`

## Flujo completo

### 1. Login

1. El backend valida RUT y password.
2. Si son correctos:
   - genera un `access token` JWT firmado con:
     - `sub = user.uuid`
     - `role`
     - `jti`
     - `iat` / `exp`
   - genera un `refresh token` opaco aleatorio.
3. El refresh token crudo se hashea con SHA-256.
4. Se crea una fila en `public.user_session` con:
   - `user_id`
   - `refresh_token_hash`
   - `expires_at`
   - `device_info` (`persistentLogin`, `userAgent`, `currentAccessJti`,
     `currentAccessExpiresAt`)
5. El controller responde:
   - body publico con `role`, `expiresInSeconds` y `user`
   - cookie `panol_access_token`
   - cookie `panol_refresh_token`

### 2. Request autenticada normal

1. El navegador envia la cookie `panol_access_token`.
2. `CookieOrHeaderBearerTokenResolver` lee primero `Authorization` si existe y,
   si no, toma la cookie de access token.
3. Spring Security valida:
   - firma del JWT
   - expiracion del JWT
   - revocacion por `jti`
4. La verificacion por `jti` consulta `public.token_revocation` usando el indice
   unico de `jti`.
5. Si pasa validacion, el request queda autenticado.

### 3. Refresh de sesion

1. El frontend recibe `401`.
2. `apiClient` dispara `POST /api/v2/auth/refresh` con cookies.
3. El backend:
   - lee `panol_refresh_token`
   - calcula `SHA-256(refreshToken)`
   - busca una fila por `refresh_token_hash` en `public.user_session`
4. Si la sesion existe y no expiro:
   - obtiene el usuario asociado
   - genera nuevo access token
   - genera nuevo refresh token
   - actualiza la misma fila usando `user_session.id`
   - reemplaza `refresh_token_hash`, `expires_at`, `currentAccessJti` y
     `currentAccessExpiresAt`
5. El cliente recibe cookies nuevas y reintenta la request original.

### 4. Logout

1. El backend intenta leer el access token actual desde cookie.
2. Si el JWT es valido:
   - extrae `jti`
   - inserta ese `jti` en `public.token_revocation`
3. Luego intenta leer el refresh token actual.
4. Si existe:
   - calcula su hash
   - borra solo esa fila de `public.user_session`
5. Siempre expira ambas cookies y responde `204`.

### 5. Listado de sesiones activas

1. El frontend autenticado llama `GET /api/v2/auth/me/sessions`.
2. El backend obtiene `userUuid` desde el `access token` ya validado.
3. Si existe refresh cookie actual:
   - calcula `SHA-256(refreshToken)`
   - marca la fila coincidente como `current=true`
4. La respuesta expone por fila:
   - `id`
   - `current`
   - `persistentLogin`
   - `userAgent`
   - `createdAt`
   - `expiresAt`

### 6. Revocacion selectiva por dispositivo

1. El frontend llama `DELETE /api/v2/auth/me/sessions/{sessionId}`.
2. El backend busca esa fila filtrando por `sessionId` y `userUuid`.
3. Si no existe o pertenece a otro usuario, responde `404`.
4. Si existe:
   - lee `currentAccessJti` desde `device_info`
   - inserta ese `jti` en `public.token_revocation` cuando esta presente
   - elimina la fila de `public.user_session`
5. Si la sesion revocada coincide con la refresh cookie actual:
   - expira `panol_access_token`
   - expira `panol_refresh_token`
6. Responde `204`.

### 7. Bootstrap de sesion en frontend

1. Al iniciar la app, el frontend llama `GET /api/v2/auth/me`.
2. Si el access token sigue valido, el backend responde el usuario actual.
3. Si responde `401`, el cliente intenta refresh silencioso una vez.
4. Si refresh funciona, reintenta `/me`.
5. Si refresh falla, limpia `auth_user` y redirige a `#/login`.

### 8. Cleanup de revocaciones expiradas

1. Un worker programado del backend corre por `fixedDelay`.
2. Toma un lote acotado de filas en `public.token_revocation` con
   `expires_at < now()`.
3. Elimina solo ese lote y registra log si borro filas.
4. No purga `user_session`; el cleanup automatico aplica solo a revocaciones de
   access token.

## Aclaracion sobre `session_id`

- `user_session.id` no autentica al usuario.
- Aunque ahora se exponga en la API para administrar sesiones propias, sigue sin
  autenticar ni renovar sesiones por si solo.
- Aunque alguien adivinara ese valor, no puede renovar sesion ni autenticarse
  solo con ese `id`.
- La credencial real sigue siendo el `refresh token` crudo de la cookie.
- El `id` interno solo se usa despues de validar el hash del refresh token
  contra la fila correcta.

## Rollout operativo

- Esta funcionalidad asume que cada fila nueva de `public.user_session` guarda
  `currentAccessJti`.
- Al desplegarla, se debe limpiar `public.user_session` y comunicar relogin
  obligatorio para que todas las sesiones activas queden registradas con la
  metadata nueva.

## Estado de seguridad actual

### Fortalezas actuales

- El access token ya no vive en `localStorage` ni `sessionStorage`.
- El refresh token no se persiste en claro en la base de datos.
- El refresh token rota en cada uso valido.
- El access token tiene `jti` unico y soporte de revocacion.
- `token_revocation` se mantiene acotada con purge automatico de filas ya
  expiradas.
- Las cookies de auth son `HttpOnly`.
- La cookie refresh esta acotada a `Path=/api/v2/auth`.
- Logout invalida solo la sesion actual y mantiene comportamiento idempotente.

### Riesgos abiertos detectados

#### 1. Secreto JWT con fallback por defecto

- `application.yaml` define un valor por defecto para `APP_AUTH_JWT_SECRET`.
- Riesgo:
  - si un entorno arranca sin secreto real, ese valor conocido permitiria firmar
    access tokens validos.
- Recomendacion:
  - eliminar el fallback y fallar al iniciar si falta el secreto real.

#### 2. CSRF deshabilitado en un modelo basado en cookies

- `SecurityConfig` desactiva CSRF globalmente.
- Riesgo:
  - el sistema depende de `SameSite=Lax` como barrera principal para requests
    autenticadas no deseadas.
  - eso reduce bastante el riesgo cross-site clasico, pero no es una defensa
    completa para todos los escenarios de navegador y subdominios.
- Recomendacion:
  - agregar proteccion CSRF para endpoints mutantes o validar `Origin` /
    `Referer` de manera estricta.

#### 3. No existe deteccion de reuse del refresh token

- El flujo rota refresh token, pero no detecta reutilizacion de tokens antiguos.
- Riesgo:
  - si un refresh token es robado y reutilizado por un atacante, el sistema no
    invalida automaticamente toda la familia de sesiones.
- Recomendacion:
  - agregar versionado o familia de refresh tokens y cortar toda la familia ante
    reuse detectado.

#### 4. `Secure` depende de configuracion explicita del entorno

- El codigo soporta `APP_AUTH_COOKIE_SECURE`, pero el default de aplicacion es
  `false`.
- Riesgo:
  - un despliegue mal configurado podria emitir cookies sin `Secure`.
- Recomendacion:
  - forzar `Secure=true` fuera de localhost o fallar al iniciar si el entorno
    no declara la politica correcta.

## Criterio actual

El flujo actual es sensiblemente mas seguro que el modelo anterior con JWT en
storage del navegador. El punto correcto de confianza no es `user_session.id`,
sino el refresh token opaco, su hash en base de datos y la validacion del `jti`
revocado para access tokens.
