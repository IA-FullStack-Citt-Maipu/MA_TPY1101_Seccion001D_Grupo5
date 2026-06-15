# 1. Introducción

## Propósito del documento

Este documento consolida, de forma técnica y trazable, la implementación completa de pruebas de rendimiento realizada para el proyecto Pañol Salud. Su objetivo es dejar evidencia del enfoque adoptado, la infraestructura de observabilidad habilitada, los scripts k6 construidos, los datasets usados y los resultados obtenidos en los distintos escenarios de carga ejecutados durante esta iteración.

La documentación está orientada a que otro integrante del equipo pueda comprender qué se implementó, por qué se implementó, cómo reproducirlo y cuáles fueron los principales hallazgos técnicos del proceso.

## Contexto del proyecto

Pañol Salud es una plataforma desarrollada para la Escuela de Salud de Duoc UC, sede Maipú, con foco en la gestión de implementos, préstamos, stock y operaciones asociadas al uso académico de recursos clínicos y de simulación. En este contexto, el rendimiento de la plataforma es relevante porque impacta directamente flujos operativos reales de docentes, coordinadores y directores.

## Norma de referencia

Como referencia documental y de estructura de evidencia se utilizó la norma ISO/IEC/IEEE 29119-3, especialmente en lo relativo a la organización de resultados, reporte de ejecución y trazabilidad entre objetivos, escenarios y hallazgos.

## Stack tecnológico relevante para el rendimiento

- Backend: Spring Boot 3.3.6, Java 21, jOOQ y PostgreSQL 17.
- Autenticación: JWT HS256, cookies HttpOnly y políticas de seguridad con soporte de RLS.
- Observabilidad: k6 para generación de carga, Prometheus para recolección de métricas y Grafana para visualización.
- Ambiente de ejecución: Docker local más `docker-compose.perf.yml` para aproximar límites de CPU, memoria y concurrencia similares al entorno GCP Cloud Run usado como referencia.

# 2. Infraestructura de Observabilidad

## Stack k6 -> Prometheus -> Grafana

La arquitectura de observabilidad implementada sigue un flujo simple y efectivo:

1. k6 ejecuta los escenarios de carga y genera tráfico HTTP controlado contra el backend.
2. El backend expone métricas internas mediante Spring Boot Actuator y Micrometer en `/actuator/prometheus`.
3. Prometheus scrapea periódicamente esas métricas.
4. Grafana consume Prometheus como datasource para analizar el comportamiento del sistema durante las pruebas.

Esta cadena permite correlacionar resultados funcionales de k6 con métricas técnicas del backend, como uso de memoria JVM, comportamiento de requests HTTP y salud general del proceso.

## Cambios realizados al backend para habilitar métricas

Durante la implementación se habilitó la exportación de métricas Prometheus en el backend mediante los siguientes cambios:

- Se agregó `io.micrometer:micrometer-registry-prometheus` en `Producto/backendpanol/pom.xml`.
- Se configuró `management.endpoints.web.exposure.include: health,info,prometheus` en `Producto/backendpanol/src/main/resources/application.yaml`.
- Se permitió acceso sin autenticación a `/actuator/prometheus` en `Producto/backendpanol/src/main/java/com/panol_project/backendpanol/bootstrap/config/SecurityConfig.java`.

## Archivos creados para observabilidad y simulación de entorno

La implementación dejó los siguientes archivos específicos para observabilidad y simulación del entorno:

- `Producto/observability/prometheus/prometheus.yml`
- `Producto/observability/grafana/provisioning/datasources/prometheus.yaml`
- `Producto/observability/grafana/provisioning/dashboards/dashboard-provider.yaml`
- `Producto/docker-compose.perf.yml`

## Simulación local de GCP Cloud Run

El archivo `Producto/docker-compose.perf.yml` se incorporó como override de Docker Compose para simular, a nivel local, una configuración acotada de recursos equivalente al backend de referencia en GCP:

- 1 vCPU de límite.
- 512 MiB de memoria de límite.
- 0.5 vCPU de reserva.
- 256 MiB de memoria de reserva.
- `SERVER_TOMCAT_THREADS_MAX=80`
- `SERVER_TOMCAT_THREADS_MIN_SPARE=10`

Esta simulación no replica todos los aspectos de Cloud Run, pero sí aproxima las restricciones principales de CPU, memoria y pool de threads para pruebas de estrés y spike.

# 3. Estrategia de Pruebas

## Justificación del alcance

El alcance se definió sobre una estimación operativa de 20 usuarios concurrentes reales para Pañol Salud. A partir de ese baseline se construyeron escenarios que cubren:

- validación funcional mínima (`Smoke Test`),
- carga esperada (`Load Test`),
- sobrecarga controlada (`Stress Test`),
- ráfaga súbita (`Spike Test`),
- y estabilidad prolongada (`Soak Test`).

## Mix de carga por rol

Se priorizó un mix alineado con el uso esperado del sistema:

- 60% Docente
- 30% Coordinador
- 10% Director

Este reparto se tradujo directamente en los VUs configurados para los escenarios multirol.

## Endpoints priorizados

Los endpoints priorizados fueron seleccionados por ser representativos de la navegación principal y de las consultas más sensibles desde el punto de vista operativo:

- `GET /api/v2/implements`
  - consulta de catálogo y disponibilidad de implementos.
- `GET /api/v2/loans`
  - visibilidad operativa de préstamos, tanto general como filtrada por usuario.
- `GET /api/v2/loans?mine=true`
  - caso de uso frecuente de docente autenticado.
- `GET /api/v2/auth/me`
  - validación de sesión autenticada y datos del usuario actual.
- `GET /api/v2/categories/active`
  - consulta de apoyo para vistas de catálogo.
- `GET /api/v2/users`
  - consulta administrativa relevante para el rol director.

## Formato de RUT requerido

Una condición crítica detectada durante la implementación fue que el backend requiere el RUT con formato:

`XXXXXXXX-D`

Ejemplo:

`22222222-2`

Enviar el RUT sin guion o sin dígito verificador provoca fallas de autenticación en los scripts de prueba.

# 4. Dataset de Prueba

## Seed base utilizado

El dataset de base usado para las pruebas se cargó desde:

`Producto/databasepanol/seeds/00_local_initial_flow_seed.sql`

Este seed entrega un flujo local inicial consistente para préstamos, implementos, categorías, ubicaciones y usuarios QA del sistema.

## Usuarios de prueba por rol

Para las pruebas se trabajó con usuarios QA representativos por rol, sin exponer contraseñas en texto plano dentro de este documento:

- Coordinador QA local:
  - RUT operativo: `11111111-1`
  - correo asociado: `coordinador.local@panolsalud.test`
- Docente QA local:
  - RUT operativo: `22222222-2`
  - correo asociado: `docente.local@panolsalud.test`
- Director QA local:
  - RUT operativo: `33333333-3`
  - correo asociado: `director.local@panolsalud.test`

Adicionalmente, durante la preparación del entorno local se amplió el dataset con usuarios docentes de apoyo para pruebas de carga, manteniendo el objetivo de aumentar variabilidad sobre el consumo de endpoints de consulta.

## Implementos y stock cargados para pruebas

Además del seed base, se incorporó un set adicional de implementos de carga para robustecer el escenario local:

- 20 implementos activos adicionales con prefijo de barcode `LOAD-IMP-`.
- mezcla de tipos `individual`, `consumable` y `reusable`.
- stock por implemento:
  - `total_stock = 50`
  - `min_stock = 5`
  - `available = 50`
  - `reserved = 0`
  - `loaned = 0`
  - `damaged = 0`

Este dataset permitió ejecutar consultas de catálogo, stock y préstamos sobre una base más representativa para pruebas multirol.

# 5. Estructura de Scripts k6

## `tests/load/k6/smoke-test.js`

Script de validación rápida del flujo autenticado principal. Ejecuta login, consulta de implementos, consulta de préstamos propios y lectura de perfil autenticado, además de logout al finalizar.

## `tests/load/k6/flows/docente.js`

Flow reutilizable del rol docente. Simula navegación típica sobre:

- catálogo de implementos,
- préstamos propios con paginación válida,
- perfil autenticado.

## `tests/load/k6/flows/coordinador.js`

Flow reutilizable del rol coordinador. Enfoca consultas sobre:

- listado general de préstamos,
- catálogo de implementos,
- categorías activas.

## `tests/load/k6/flows/director.js`

Flow reutilizable del rol director. Modela consultas administrativas sobre:

- usuarios,
- préstamos generales,
- perfil autenticado.

## `tests/load/k6/scenarios/esc-02-load.js`

Escenario multirol de carga esperada. Usa el mix 60/30/10 con una duración total de 10 minutos y representa el comportamiento normal intensificado del sistema.

## `tests/load/k6/scenarios/esc-03-stress.js`

Escenario multirol de sobrecarga controlada. Incrementa VUs hasta 60 concurrentes totales, por encima del baseline operativo real, para medir tolerancia bajo presión sostenida.

## `tests/load/k6/scenarios/esc-04-spike.js`

Escenario multirol de ráfaga. Simula un salto abrupto de concurrencia y posterior recuperación, útil para evaluar absorción de picos bajo el perfil de recursos restringidos.

## `tests/load/k6/scenarios/esc-05-soak.js`

Escenario multirol prolongado. Mantiene 20 VUs durante 30 minutos para observar estabilidad, degradación progresiva y síntomas de fuga de memoria o agotamiento de recursos.

## Nota sobre ESC-01

`ESC-01` corresponde explícitamente al `Smoke Test`, implementado en `tests/load/k6/smoke-test.js`.

# 6. Ejecución de Escenarios y Resultados

## ESC-01 - Smoke Test

- ID: `ESC-01`
- Nombre: `Smoke Test`
- Tipo: validación rápida funcional y de rendimiento base
- VUs: `3`
- Duración: `1 minuto`

### Comando de ejecución

```powershell
k6 run `
  -e DOCENTE_RUT=<RUT_DOCENTE> `
  -e DOCENTE_PASSWORD=<PASSWORD> `
  --out json=tests/load/k6/results/smoke-test-result.json `
  tests/load/k6/smoke-test.js
```

### Resultados

| Métrica | Valor |
| --- | --- |
| p50 | 14ms |
| p95 | 82.59ms |
| p99 | No registrado en el resumen consolidado |
| Error rate | 0% |
| Throughput | 2.93 req/s |
| Total checks | 241/241 |
| Total iterations | 60 |

### Umbrales

- `http_req_failed: rate < 0.01` -> Pasó
- `http_req_duration: p(95) < 5000` -> Pasó

**Estado final:** Pasó ✅

## ESC-02 - Load Test

- ID: `ESC-02`
- Nombre: `Load Test`
- Tipo: carga nominal intensificada
- VUs: `50` (`30 docentes`, `15 coordinadores`, `5 directores`)
- Duración: `10 minutos`

### Comando de ejecución

```powershell
k6 run `
  -e DOCENTE_RUT=<RUT_DOCENTE> `
  -e DOCENTE_PASSWORD=<PASSWORD> `
  -e COORD_RUT=<RUT_COORDINADOR> `
  -e COORD_PASSWORD=<PASSWORD> `
  -e DIRECTOR_RUT=<RUT_DIRECTOR> `
  -e DIRECTOR_PASSWORD=<PASSWORD> `
  --out json=tests/load/k6/results/esc-02-load-result.json `
  tests/load/k6/scenarios/esc-02-load.js
```

### Resultados

| Métrica | Valor |
| --- | --- |
| p50 | 4.45ms |
| p95 | 7.16ms |
| p99 | No registrado en el resumen consolidado |
| Error rate | 0% |
| Throughput | 26.54 req/s |
| Total checks | 16092/16092 |
| Total iterations | 5363 |

### Umbrales

- `http_req_failed: rate < 0.01` -> Pasó
- `http_req_duration: p(95) < 5000` -> Pasó

**Estado final:** Pasó ✅

## ESC-03 - Stress Test

- ID: `ESC-03`
- Nombre: `Stress Test`
- Tipo: sobrecarga controlada
- VUs: `60` (`36 docentes`, `18 coordinadores`, `6 directores`)
- Duración: `12 minutos`

### Comando de ejecución

```powershell
k6 run `
  -e DOCENTE_RUT=<RUT_DOCENTE> `
  -e DOCENTE_PASSWORD=<PASSWORD> `
  -e COORD_RUT=<RUT_COORDINADOR> `
  -e COORD_PASSWORD=<PASSWORD> `
  -e DIRECTOR_RUT=<RUT_DIRECTOR> `
  -e DIRECTOR_PASSWORD=<PASSWORD> `
  --out json=tests/load/k6/results/esc-03-stress-result.json `
  tests/load/k6/scenarios/esc-03-stress.js
```

### Resultados

| Métrica | Valor |
| --- | --- |
| p50 | 3.7ms |
| p95 | 6.17ms |
| p99 | No registrado en el resumen consolidado |
| Error rate | 0% |
| Throughput | 28.82 req/s |
| Total checks | 20850/20850 |
| Total iterations | 6949 |

### Umbrales

- `http_req_failed: rate < 0.05` -> Pasó
- `http_req_duration: p(95) < 5000` -> Pasó

**Estado final:** Pasó ✅

## ESC-04 - Spike Test

- ID: `ESC-04`
- Nombre: `Spike Test`
- Tipo: ráfaga súbita y recuperación
- VUs: `80 pico` (`48 docentes`, `24 coordinadores`, `8 directores`)
- Duración: `5m30s`
- Ambiente: `docker-compose.perf.yml` (`1 vCPU`, `512Mi`, simulando GCP Cloud Run)

### Comando de ejecución

```powershell
k6 run `
  -e DOCENTE_RUT=<RUT_DOCENTE> `
  -e DOCENTE_PASSWORD=<PASSWORD> `
  -e COORD_RUT=<RUT_COORDINADOR> `
  -e COORD_PASSWORD=<PASSWORD> `
  -e DIRECTOR_RUT=<RUT_DIRECTOR> `
  -e DIRECTOR_PASSWORD=<PASSWORD> `
  --out json=tests/load/k6/results/esc-04-spike-result.json `
  tests/load/k6/scenarios/esc-04-spike.js
```

### Resultados

| Métrica | Valor |
| --- | --- |
| p50 | 4.34ms |
| p95 | 8.77ms |
| p99 | No registrado en el resumen consolidado |
| Error rate | 0% |
| Throughput | 30.59 req/s |
| Total checks | 10266/10266 |
| Total iterations | 3421 |

### Umbrales

- `http_req_failed: rate < 0.10` -> Pasó
- `http_req_duration: p(95) < 8000` -> Pasó

**Estado final:** Pasó ✅

## ESC-05 - Soak Test

- ID: `ESC-05`
- Nombre: `Soak Test`
- Tipo: estabilidad prolongada
- VUs: `20` (`12 docentes`, `6 coordinadores`, `2 directores`)
- Duración: `30 minutos`

### Comando de ejecución

```powershell
k6 run `
  -e DOCENTE_RUT=<RUT_DOCENTE> `
  -e DOCENTE_PASSWORD=<PASSWORD> `
  -e COORD_RUT=<RUT_COORDINADOR> `
  -e COORD_PASSWORD=<PASSWORD> `
  -e DIRECTOR_RUT=<RUT_DIRECTOR> `
  -e DIRECTOR_PASSWORD=<PASSWORD> `
  --out json=tests/load/k6/results/esc-05-soak-result.json `
  tests/load/k6/scenarios/esc-05-soak.js
```

### Resultados

| Métrica | Valor |
| --- | --- |
| p50 | 3.73ms |
| p95 | 5.98ms |
| p99 | No registrado en el resumen consolidado |
| Error rate | 0% |
| Throughput | 12.47 req/s |
| Total checks | 22479/22479 |
| Total iterations | 7492 |

### Umbrales

- `http_req_failed: rate < 0.01` -> Pasó
- `http_req_duration: p(95) < 5000` -> Pasó

**Estado final:** Pasó ✅

# 7. Análisis de Resultados

## Tabla comparativa de escenarios

| Escenario | Tipo | VUs máximos | Duración | p50 | p95 | Error rate | Throughput | Checks | Estado |
| --- | --- | ---: | --- | --- | --- | --- | --- | --- | --- |
| ESC-01 | Smoke | 3 | 1m | 14ms | 82.59ms | 0% | 2.93 req/s | 241/241 | Pasó |
| ESC-02 | Load | 50 | 10m | 4.45ms | 7.16ms | 0% | 26.54 req/s | 16092/16092 | Pasó |
| ESC-03 | Stress | 60 | 12m | 3.7ms | 6.17ms | 0% | 28.82 req/s | 20850/20850 | Pasó |
| ESC-04 | Spike | 80 | 5m30s | 4.34ms | 8.77ms | 0% | 30.59 req/s | 10266/10266 | Pasó |
| ESC-05 | Soak | 20 | 30m | 3.73ms | 5.98ms | 0% | 12.47 req/s | 22479/22479 | Pasó |

## Hallazgos principales

### 1. Warning de HikariCP detectado y resuelto

Durante la preparación del ambiente se detectaron señales de ajuste pendiente en el pool JDBC para el perfil local Docker. La solución aplicada fue explicitar parámetros Hikari en el perfil `docker` del backend:

- `max-lifetime: 600000`
- `connection-timeout: 20000`
- `idle-timeout: 300000`
- `minimum-idle: 2`

Esto ayudó a estabilizar el comportamiento del datasource local bajo carga sostenida.

### 2. Paginación base-1 detectada y corregida

Inicialmente algunos scripts multirol consultaban `/api/v2/loans?page=0&size=10`. El backend valida paginación base-1, por lo que respondió con error `400` y mensaje equivalente a `page debe ser mayor o igual a 1`. La corrección consistió en actualizar los scripts a `page=1`.

### 3. Formato de RUT obligatorio

Se verificó que el login falla si el RUT no se envía con guion y dígito verificador. Este hallazgo quedó incorporado tanto en la documentación como en los comandos de ejecución.

### 4. Latencia observada en Docker local vs GCP estimado

Los resultados obtenidos corresponden a un entorno local Docker. Aunque `docker-compose.perf.yml` aproxima restricciones de CPU, memoria y concurrencia de Cloud Run, no replica completamente la red administrada, el runtime serverless ni la elasticidad del entorno productivo. Por ello, los valores de latencia deben interpretarse como referencia técnica controlada y no como sustituto exacto del comportamiento en GCP.

### 5. Sin degradación progresiva en Soak Test

El `Soak Test` de 30 minutos cerró con:

- error rate `0%`
- p95 `5.98ms`
- checks `22479/22479`

No se observaron señales de degradación progresiva en el resumen consolidado, lo que es consistente con ausencia de fugas evidentes de memoria o agotamiento progresivo del sistema bajo la carga nominal sostenida.

## Conclusión general

La implementación de performance testing muestra que Pañol Salud respondió satisfactoriamente en los cinco escenarios definidos. El sistema superó pruebas de humo, carga nominal, sobrecarga controlada, picos abruptos y estabilidad prolongada sin errores en el resumen consolidado, manteniendo latencias p95 bajas y throughput consistente para el tipo de flujos priorizados.

# 8. Cómo Ejecutar las Pruebas

## Prerrequisitos

- Docker Desktop en ejecución.
- k6 `v2.0.0+` instalado y disponible en PATH.
- Base de datos local disponible.
- Credenciales válidas para los tres roles de prueba.

## Levantar el stack

```bash
cd Producto/databasepanol && docker compose up -d
cd .. && docker compose up -d
```

## Cargar dataset de prueba

Para cargar el seed base local:

```bash
cd Producto
docker compose -f databasepanol/docker-compose.yaml exec -T postgres \
psql -U panol_user -d panol -f /dev/stdin < databasepanol/seeds/00_local_initial_flow_seed.sql
```

Si se requiere ampliar el dataset para pruebas de carga, se debe ejecutar además el batch SQL complementario de implementos, stock y usuarios de apoyo utilizado durante esta implementación.

## Ejecutar cada escenario

### ESC-01 Smoke Test

```powershell
k6 run `
  -e DOCENTE_RUT=<RUT_DOCENTE> `
  -e DOCENTE_PASSWORD=<PASSWORD> `
  --out json=tests/load/k6/results/smoke-test-result.json `
  tests/load/k6/smoke-test.js
```

### ESC-02 Load Test

```powershell
k6 run `
  -e DOCENTE_RUT=<RUT_DOCENTE> `
  -e DOCENTE_PASSWORD=<PASSWORD> `
  -e COORD_RUT=<RUT_COORDINADOR> `
  -e COORD_PASSWORD=<PASSWORD> `
  -e DIRECTOR_RUT=<RUT_DIRECTOR> `
  -e DIRECTOR_PASSWORD=<PASSWORD> `
  --out json=tests/load/k6/results/esc-02-load-result.json `
  tests/load/k6/scenarios/esc-02-load.js
```

### ESC-03 Stress Test

```powershell
k6 run `
  -e DOCENTE_RUT=<RUT_DOCENTE> `
  -e DOCENTE_PASSWORD=<PASSWORD> `
  -e COORD_RUT=<RUT_COORDINADOR> `
  -e COORD_PASSWORD=<PASSWORD> `
  -e DIRECTOR_RUT=<RUT_DIRECTOR> `
  -e DIRECTOR_PASSWORD=<PASSWORD> `
  --out json=tests/load/k6/results/esc-03-stress-result.json `
  tests/load/k6/scenarios/esc-03-stress.js
```

### ESC-04 Spike Test

```powershell
k6 run `
  -e DOCENTE_RUT=<RUT_DOCENTE> `
  -e DOCENTE_PASSWORD=<PASSWORD> `
  -e COORD_RUT=<RUT_COORDINADOR> `
  -e COORD_PASSWORD=<PASSWORD> `
  -e DIRECTOR_RUT=<RUT_DIRECTOR> `
  -e DIRECTOR_PASSWORD=<PASSWORD> `
  --out json=tests/load/k6/results/esc-04-spike-result.json `
  tests/load/k6/scenarios/esc-04-spike.js
```

### ESC-05 Soak Test

```powershell
k6 run `
  -e DOCENTE_RUT=<RUT_DOCENTE> `
  -e DOCENTE_PASSWORD=<PASSWORD> `
  -e COORD_RUT=<RUT_COORDINADOR> `
  -e COORD_PASSWORD=<PASSWORD> `
  -e DIRECTOR_RUT=<RUT_DIRECTOR> `
  -e DIRECTOR_PASSWORD=<PASSWORD> `
  --out json=tests/load/k6/results/esc-05-soak-result.json `
  tests/load/k6/scenarios/esc-05-soak.js
```

## Para simular entorno GCP

```bash
docker compose -f docker-compose.yaml -f docker-compose.perf.yml \
  up -d --no-deps --build backend
```

# 9. Problemas Encontrados y Soluciones

## 1. `APP_DB_ENV=supabase` en `.env.local`

**Problema:** el backend estaba apuntando al perfil `supabase` en vez de usar la base local para las pruebas.

**Solución:** se ajustó el valor para usar `docker`, de modo que los escenarios corrieran sobre la base PostgreSQL local preparada para testing.

## 2. `DB_DOCKER_PASSWORD` comentado en `.env.local`

**Problema:** el backend no contaba con la contraseña efectiva de conexión local al datasource Docker.

**Solución:** se descomentó la variable para que el contenedor backend pudiera iniciar correctamente con la base local.

## 3. Contraseña del seed perdida

**Problema:** las credenciales QA del seed no estaban utilizables directamente para automatizar login en k6.

**Solución:** se regeneró y normalizó el hash BCrypt de los usuarios QA locales, dejando un set funcional para ejecutar los escenarios de prueba.

## 4. RUT sin dígito verificador

**Problema:** se intentó autenticar con RUT sin formato completo.

**Solución:** se corrigieron comandos y documentación para usar siempre `XXXXXXXX-D`.

## 5. Paginación `page=0` inválida

**Problema:** el backend usa paginación base-1 y rechazó `page=0` con `400`.

**Solución:** se corrigieron los scripts para usar `page=1`.

## 6. `/actuator/prometheus` retornaba `401`

**Problema:** Prometheus no podía scrapear métricas porque el endpoint estaba protegido por Spring Security.

**Solución:** se agregó `/actuator/prometheus` a la whitelist pública del `SecurityConfig`.

## 7. k6 no instalado

**Problema:** el entorno local no tenía k6 disponible inicialmente.

**Solución:** se instaló mediante `winget` con el paquete `GrafanaLabs.k6`.

# 10. Estructura de Archivos Creados

Árbol consolidado de archivos nuevos relevantes para esta implementación, confirmado sobre el filesystem actual:

```text
Producto/
├─ docker-compose.perf.yml
└─ tests/
   └─ load/
      ├─ PERFORMANCE_TESTING.md
      └─ k6/
         ├─ smoke-test.js
         ├─ flows/
         │  ├─ coordinador.js
         │  ├─ director.js
         │  └─ docente.js
         ├─ results/
         │  ├─ esc-02-load-result.json
         │  ├─ esc-03-stress-result.json
         │  ├─ esc-04-spike-result.json
         │  ├─ esc-05-soak-result.json
         │  ├─ smoke-test-result-2.json
         │  ├─ smoke-test-result-3.json
         │  └─ smoke-test-result.json
         └─ scenarios/
            ├─ esc-02-load.js
            ├─ esc-03-stress.js
            ├─ esc-04-spike.js
            └─ esc-05-soak.js
```

```text
Producto/observability/
├─ grafana/
│  └─ provisioning/
│     ├─ dashboards/
│     │  └─ dashboard-provider.yaml
│     └─ datasources/
│        └─ prometheus.yaml
└─ prometheus/
   └─ prometheus.yml
```
