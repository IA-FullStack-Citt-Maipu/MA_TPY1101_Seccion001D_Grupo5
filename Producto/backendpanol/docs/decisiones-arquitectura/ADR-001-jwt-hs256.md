- Estado del documento: vigente
- Ultima verificacion: 2026-05-15
- Fuente de verdad: ver matriz canonica vigente y codigo fuente actual

# ADR-001: Decisión de JWT con HS256

- Estado: Aceptado
- Fecha: 2026-05-07
- Alcance: Backend de autenticación de Pañol Salud

## Contexto

El sistema implementa autenticación por RUT y contraseña, emisión de JWT con rol y expiración, y revocación por logout.

En la etapa actual, la arquitectura es de backend centralizado (sin múltiples verificadores externos de token) y con despliegue controlado por el equipo del proyecto.

## Decisión

Se adopta `HS256` como algoritmo de firma de JWT para la etapa actual.

## Justificación

1. Simplicidad operativa: no requiere infraestructura adicional de llaves públicas/publicación JWKS.
2. Menor complejidad de mantenimiento para el equipo actual.
3. Mejor rendimiento por operación (HMAC simétrico), útil en infraestructura acotada.
4. Alineado con el estado actual del sistema (emisor y verificador acoplados en el mismo dominio operativo).

## Consecuencias

### Positivas

1. Implementación más rápida y directa.
2. Menor riesgo de errores iniciales de configuración criptográfica.
3. Coste de cómputo bajo por firma/verificación.

### Negativas

1. El secreto de firma es compartido entre emisión y verificación.
2. Si el secreto se filtra, un actor malicioso podría firmar tokens válidos.
3. Escala peor cuando hay múltiples servicios verificadores independientes.

## Controles requeridos con HS256

1. Guardar el secreto en gestor de secretos, nunca en repositorio.
2. Rotación periódica de secreto (definir ventana y procedimiento).
3. Restricción de acceso al secreto por mínimo privilegio.
4. Monitoreo de uso anómalo de tokens y eventos de autenticación.

## Cuándo revisar esta decisión

Esta decisión debe revisarse cuando ocurra al menos una de estas condiciones:

1. Múltiples microservicios verifiquen JWT de forma independiente.
2. Integración con terceros que requieran validar tokens sin compartir secreto.
3. Exigencias de compliance/auditoría que favorezcan firma asimétrica.
4. Necesidad de rotación de claves con `kid`/JWKS y continuidad sin acoplamiento fuerte.

## Relación con decisiones futuras

Si el sistema escala, ejecutar el plan definido en `ADR-002-plan-migracion-rs256.md`.

