- Estado del documento: vigente
- Ultima verificacion: 2026-05-15
- Fuente de verdad: ver matriz canonica vigente y codigo fuente actual

# ADR-002: Plan de migración de HS256 a RS256

- Estado: Propuesto
- Fecha: 2026-05-07
- Alcance: Plataforma de autenticación y verificación JWT

## Objetivo

Definir una migración segura y sin downtime desde `HS256` a `RS256` cuando el sistema requiera mayor separación de responsabilidades entre emisor y verificadores.

## Motivación

`RS256` permite:

1. Firmar con llave privada en el emisor.
2. Verificar con llave pública en consumidores.
3. Evitar distribución del mismo secreto de firma a múltiples servicios.
4. Rotar llaves de forma controlada con `kid` y JWKS.

## Estrategia general

Migración en fases con compatibilidad temporal dual.

## Fase 0: Preparación

1. Introducir abstracción de firma/verificación (`TokenService`) desacoplada de algoritmo.
2. Habilitar configuración por entorno (`JWT_ALGORITHM=HS256|RS256`).
3. Soportar header `kid` en tokens nuevos.
4. Definir repositorio seguro de llaves (KMS/Secret Manager).

## Fase 1: Infraestructura criptográfica

1. Generar par de llaves RSA (private/public) en entorno dev.
2. Definir formato de publicación de públicas (JWKS endpoint interno o proveedor).
3. Configurar cache de llaves públicas en verificadores (TTL + refresh).

## Fase 2: Verificación dual

1. Actualizar verificadores para aceptar temporalmente:
   - tokens `HS256` existentes
   - tokens `RS256` nuevos
2. Registrar métricas por algoritmo detectado en producción.

## Fase 3: Emisión con RS256

1. Cambiar emisor para firmar nuevos JWT con `RS256`.
2. Incluir `kid` en header JWT.
3. Mantener aceptación de `HS256` durante ventana de transición.

## Fase 4: Corte de HS256

1. Esperar expiración total de tokens HS256 emitidos antes del corte.
2. Deshabilitar verificación HS256.
3. Mantener monitoreo de rechazos por algoritmo no soportado.

## Fase 5: Endurecimiento

1. Implementar política de rotación de llaves RSA (ej. cada 90 días).
2. Mantener al menos dos llaves públicas válidas durante rotaciones.
3. Auditar accesos a llave privada y eventos de firma.

## Riesgos y mitigaciones

1. Riesgo: tokens inválidos durante transición.
   - Mitigación: verificación dual y rollout gradual.
2. Riesgo: cache desactualizada de llaves públicas.
   - Mitigación: TTL corto + fallback refresh + alarmas.
3. Riesgo: rotación incompleta (`kid` no publicado).
   - Mitigación: checklist pre-deploy y pruebas de integración.

## Pruebas requeridas

1. Unit tests de emisión/verificación para ambos algoritmos.
2. Pruebas E2E login/logout con tokens RS256.
3. Prueba de rollback (volver a emisión HS256 temporalmente).
4. Prueba de expiración y revocación de JWT.

## Criterios de éxito

1. Login/logout operativos sin interrupción en producción.
2. Cero downtime por cambio de algoritmo.
3. Todos los servicios verificadores operando con RS256.
4. HS256 completamente retirado según ventana planificada.

## Checklist operativo resumido

1. Preparar llaves y publicación JWKS.
2. Activar verificación dual.
3. Cambiar emisión a RS256.
4. Esperar expiración HS256.
5. Desactivar HS256.
6. Validar métricas, logs y alertas.

