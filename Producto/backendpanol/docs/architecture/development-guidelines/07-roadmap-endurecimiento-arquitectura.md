- Estado del documento: vigente
- Ultima verificacion: 2026-05-16
- Fuente de verdad: matriz canonica vigente y código actual

# 07 - Roadmap de Endurecimiento Arquitectónico

## Horizonte 0-30 días

1. Congelar deuda nueva:
- No nuevos servicios `application` con `DSLContext` directo.

2. Trazabilidad mínima:
- Estandarizar eventos de auditoría y contrato `outbox_event`.

3. Checklist PR obligatorio.

## Horizonte 30-60 días

1. [FINALIZADO] Consolidar puertos en casos críticos (`users`, `auth`, `loan`).
2. Unificar naming de repositorios/adaptadores.
3. Crear tests de integración por adapter.

## Horizonte 60-90 días

1. Mantener `outbox_event` como mecanismo de salida eventual.
2. Entrega final a destino/configuración de integración.
3. Métricas de retries, pendientes y latencia de worker.

## Horizonte 90+ días

1. Revisar límites de dominios (separar `loan` explícito si aún está mezclado).
2. Evaluar modularización adicional por bounded context.
3. ADRs formales para decisiones estructurales mayores.

## KPIs de seguimiento

- % casos de uso con puertos (sin acoplamiento técnico).
- % PRs con checklist completo.
- p95 endpoints críticos.
- tasa de fallas de publicación de eventos.
