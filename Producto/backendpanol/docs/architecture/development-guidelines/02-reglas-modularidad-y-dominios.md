- Estado del documento: vigente
- Ultima verificacion: 2026-05-15
- Fuente de verdad: ver matriz canonica vigente y codigo fuente actual

# 02 - Reglas de Modularidad y Dominios

## Módulos

Cada dominio debe vivir en su propio módulo (`modules/<dominio>`), por ejemplo:
- `auth`
- `users`
- `catalog`
- `loan` (recomendado explicitar)

## Reglas

1. No importar infraestructura de otro módulo.
2. No acceder directo a tablas de otro módulo desde servicios de aplicación.
3. Integración entre módulos por:
- interfaces/puertos,
- DTO de aplicación,
- eventos de dominio.

## Estructura mínima por módulo

- `api/` (entrada HTTP)
- `application/` (casos de uso)
- `domain/` (modelo + puertos)
- `infrastructure/` (adapters db, clients)

## Antipatrones

- `application` con `DSLContext` directo (permitido solo temporalmente y marcado con deuda técnica).
- utilidades globales que mezclen reglas de varios dominios.
- SQL incrustado en controladores.

## Política de cambios cross-domain

Cuando un caso de uso afecta más de un dominio:
1. Definir dueño del caso de uso.
2. Exponer contrato explícito de colaboración.
3. Evitar acceso lateral no mediado.

