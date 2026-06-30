# Modulo: users

- Estado del documento: vigente
- Ultima verificacion: 2026-06-29
- Fuente de verdad: `UserAdminV2Controller`, `UserAdminService`, `ArchitectureTest`

## Responsabilidad

Administracion de usuarios (alta, actualizacion, cambio de rol, activacion/desactivacion y eliminacion definitiva condicionada).

## API vigente

Base path: `/api/v2/users`

- `GET /`
- `POST /`
- `PUT /{userUuid}`
- `PUT /{userUuid}/role`
- `PATCH /{userUuid}/active`
- `DELETE /{userUuid}`

Semantica vigente:

- `PATCH /{userUuid}/active`: activa o desactiva el usuario.
- `DELETE /{userUuid}`: elimina fisicamente solo si el usuario ya esta inactivo y no tiene referencias bloqueantes.

Reglas funcionales relevantes:

- Un director no puede desactivar ni eliminar su propio usuario desde este modulo.
- El usuario tecnico configurado para outbox (`SISTEMA_OUTBOX`) no admite acciones destructivas.
- Si un usuario tiene historial asociado en prestamos, movimientos, auditoria u otros registros bloqueantes, el backend responde conflicto controlado en lugar de depender del error SQL.

## Fronteras

- Auditoria por `auth.domain.AuditLogPort` (excepcion documentada y permitida).
- Sin dependencias a API de otros modulos.
- Eventos de cambios de usuarios via outbox.

