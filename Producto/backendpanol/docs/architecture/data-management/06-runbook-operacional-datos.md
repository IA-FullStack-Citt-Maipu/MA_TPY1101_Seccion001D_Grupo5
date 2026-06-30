- Estado del documento: vigente
- Ultima verificacion: 2026-05-15
- Fuente de verdad: ver matriz canonica vigente y codigo fuente actual

# 06 - Runbook Operacional de Datos

## 1. Antes de desplegar

- Validar migraciones Flyway en entorno de staging.
- Verificar compatibilidad jOOQ con schema actual.
- Revisar índices nuevos y costo de creación.
- Confirmar secretos/credenciales por entorno.

## 2. Despliegue

1. Deploy backend con migraciones.
2. Validar startup y `flyway_schema_history`.
3. Ejecutar smoke tests:
   - login,
   - CRUD clave,
   - lectura dashboard.
4. Verificar tabla `outbox_event` y estado de eventos pendientes.

## 3. Checklist post deploy

- SQL:
  - conteo de filas en tablas críticas,
  - locks/errores en logs,
  - latencia consultas principales.
- Outbox:
  - eventos pendientes, `failed_count` y latencia de publicación,
  - tasa de reintentos por evento y estabilidad del worker.

## 4. Incidentes comunes

### SQL lento
- Revisar plan de ejecución (`EXPLAIN ANALYZE`).
- Ajustar/crear índice.
- Revisar N+1 desde backend.

### Backlog de outbox creciente
- Ejecutar checks de saturación por tabla/outbox.
- Revisar causas de reintentos repetidos y errores de publicación.
- Si hay eventos `FAILED`, corregir causa y reencolar.

### Inconsistencia SQL vs Outbox
- Ejecutar script de conciliación por ventana temporal.
- Reprocesar eventos faltantes desde `outbox_event` tras corregir causa raíz.

## 5. Backups y recuperación

- PostgreSQL:
  - backup lógico diario + snapshot infra.
  - pruebas de restore mensuales.
- Outbox/eventos:
  - backup lógico del esquema/migación de outbox si aplica.
  - pruebas de recuperación de eventos no entregados.

## 6. Gobierno y auditoría

- Cambios de esquema solo por migración versionada.
- Cambios de contrato de eventos documentados con versión de payload.
- Mantener trazabilidad de decisiones técnicas (ADR).
