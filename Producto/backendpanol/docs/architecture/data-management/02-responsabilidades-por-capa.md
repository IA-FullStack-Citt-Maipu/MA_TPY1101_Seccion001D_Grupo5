- Estado del documento: vigente
- Ultima verificacion: 2026-05-15
- Fuente de verdad: ver matriz canonica vigente y codigo fuente actual

# 02 - Responsabilidades por Capa

## Backend (Spring Boot)

Responsable de:
- Autenticación/autorización.
- Validaciones de negocio.
- Orquestación transaccional entre SQL y mecanismos de eventos.
- Exposición de contratos API.
- Manejo de errores y observabilidad.

No debe:
- Duplicar reglas que pertenecen a constraints críticas de SQL.
- Delegar decisiones de negocio en triggers complejos sin trazabilidad.

## PostgreSQL (Relacional)

Responsable de:
- Entidades maestras y relaciones.
- Integridad referencial (FK).
- Consistencia transaccional ACID.
- Restricciones de dominio (CHECK, UNIQUE, NOT NULL).
- Vistas/consultas agregadas críticas.

No debe:
- Almacenar trazabilidad de alta cardinalidad orientada a eventos si impacta rendimiento OLTP.

## Outbox (Persistencia transaccional de eventos)

Responsable de:
- Persistencia de eventos de integración en PostgreSQL (`outbox_event`).
- Reintentos con estados `PENDING`, `PROCESSING`, `SENT`, `FAILED`.
- Orquestación con consumidores o workers externos.

No debe:
- Reemplazar tablas maestras SQL.
- Ser la fuente de verdad de estados transaccionales críticos.

## Tabla de decisión rápida

- ¿Requiere FK/ACID fuerte? -> PostgreSQL.
- ¿Es timeline/evento/auditoría/notificación? -> `outbox_event` (consumidor externo).
- ¿Es cálculo cross-domain para UI? -> Backend (servicio agregador).
