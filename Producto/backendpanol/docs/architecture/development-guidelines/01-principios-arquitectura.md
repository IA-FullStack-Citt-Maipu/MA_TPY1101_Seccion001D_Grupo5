- Estado del documento: vigente
- Ultima verificacion: 2026-05-15
- Fuente de verdad: ver matriz canonica vigente y codigo fuente actual

# 01 - Principios de Arquitectura

## Objetivo

Asegurar que cada incremento preserve decisiones base:
- módulos por dominio,
- casos de uso aislados,
- infraestructura intercambiable,
- eventos confiables.

## Principios obligatorios

1. **Dominio primero**
- Reglas de negocio viven en `domain`/`application`, no en controladores.

2. **Dependencias hacia adentro**
- `api` e `infrastructure` dependen de `application/domain`.
- `domain` nunca depende de frameworks.

3. **Estado canónico en SQL**
- Entidades transaccionales críticas se resuelven en PostgreSQL.

4. **Eventos para integración, no para verdad transaccional**
- `outbox_event` y el transporte eventual habilitan integración/trazabilidad.
- La verdad transaccional principal está en PostgreSQL.

5. **Migraciones por corte controlado**
- Priorizar cambios incrementales por módulo, con feature parity.
- Los cambios masivos de esquema deben ir con plan de corte, rollback y validación (ej. `db/migration/v25/V25__schema_alignment_big_bang.sql`).

## Regla de oro

Si una decisión mezcla reglas de negocio con detalles técnicos (DB, HTTP, framework), separar antes de mergear.
