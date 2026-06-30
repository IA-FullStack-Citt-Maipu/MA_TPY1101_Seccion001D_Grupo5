- Estado del documento: vigente
- Ultima verificacion: 2026-05-15
- Fuente de verdad: ver matriz canonica vigente y codigo fuente actual

# JWT Claims - Pañol Salud

- Última actualización: 2026-05-10
- Fuente: `AuthService` + configuración de seguridad backend

## Claims emitidos por `/api/v2/auth/login` (UUID-only)

1. `iss` (string)
- Emisor del token.
- Configurable por `APP_AUTH_JWT_ISSUER`.
- Valor por defecto: `panol-backend`.

2. `sub` (string)
- Identificador principal del usuario autenticado.
- Corresponde al `uuid` canónico del usuario en PostgreSQL.

3. `role` (string)
- Rol normalizado del usuario.
- Valores esperados: `COORDINADOR`, `DIRECTOR`, `DOCENTE`.

4. `jti` (string)
- Identificador único del token (UUID).
- Se usa para revocación en logout.

5. `iat` (number, epoch seconds)
- Fecha/hora de emisión.

6. `exp` (number, epoch seconds)
- Fecha/hora de expiración.

## Header JWT

1. `alg`: `HS256`

## Notas

- El sistema actual usa firma simétrica `HS256`.
- El claim `sub` es la única fuente de identidad de usuario en el token.
- Para estrategia de migración a `RS256`, revisar:
  - `docs/decisiones-arquitectura/ADR-002-plan-migracion-rs256.md`

