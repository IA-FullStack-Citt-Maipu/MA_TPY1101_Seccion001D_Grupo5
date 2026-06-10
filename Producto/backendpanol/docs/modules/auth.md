# Modulo: auth

- Estado del documento: vigente
- Ultima verificacion: 2026-06-10
- Fuente de verdad: `AuthV2Controller`, `AuthService`, `SecurityConfig`, `AuditLogPort`

## Responsabilidad

Autenticacion con JWT, bloqueo temporal por intentos fallidos, revocacion de token
en logout y autogestion del usuario autenticado (`/me`, cambio de correo y cambio
de contrasena).

## API vigente

Base path: `/api/v2/auth`

- `POST /login`
- `POST /logout`
- `GET /me`
- `PATCH /me/email`
- `PATCH /me/password`

## Reglas clave

- Login es el unico endpoint auth con `permitAll`.
- Logout, `/me`, `/me/email` y `/me/password` requieren JWT valido.
- Login devuelve `accessToken`, `role`, `expiresInSeconds` y `user`
  para bootstrap de sesion en frontend.
- `PATCH /me/email` normaliza el correo a lowercase y rechaza duplicados.
- `PATCH /me/password` exige contrasena actual valida, minimo 8 caracteres y
  no permite reutilizar la misma contrasena.
- Logout revoca el token por `jti`.
- Auditoria via puerto de dominio (`AuditLogPort`).
- Eventos de auth y usuario via outbox cuando aplica (`UserLoggedIn`,
  `UserLoggedOut`, `UserEmailChanged`, `UserPasswordChanged`, `LoginFailed`).
