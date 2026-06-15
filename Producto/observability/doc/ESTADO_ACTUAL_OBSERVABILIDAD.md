# Estado actual de observabilidad

- Proyecto: Pañol Salud
- Fecha de referencia: 2026-06-15
- Objetivo: describir el estado real del stack de observabilidad y performance testing para que una IA pueda recomendar el siguiente paso con contexto preciso.

## 1. Resumen ejecutivo

Hoy el proyecto tiene un stack local funcional para pruebas de rendimiento y observabilidad compuesto por:

- backend Spring Boot con endpoint Prometheus expuesto;
- Prometheus para recolección de métricas;
- Grafana para visualización;
- k6 para pruebas de carga, smoke, stress, spike y soak;
- dashboards versionados para backend y performance testing;
- un perfil Docker de performance que limita recursos del backend para aproximar un entorno acotado.

El flujo operativo actual es:

1. PostgreSQL local se levanta desde `Producto/databasepanol/docker-compose.yaml`.
2. El backend se levanta con `APP_DB_ENV=docker` y aplica migraciones Flyway.
3. El backend expone métricas en `/actuator/prometheus`.
4. Prometheus scrapea esas métricas desde `backend:8080`.
5. Prometheus también puede recibir métricas de `k6` por remote write.
6. Grafana consume Prometheus como datasource y provisiona dashboards locales.
7. k6 ejecuta escenarios contra el backend local.

## 2. Qué está implementado

### Backend

El backend ya está configurado para observabilidad básica:

- expone `health`, `info` y `prometheus` en Actuator;
- permite acceso público a `/actuator/prometheus`;
- corre con perfil `docker` para usar PostgreSQL local cuando corresponde;
- aplica Flyway con la cadena SQL vigente;
- usa jOOQ y HikariCP en el arranque normal del servicio.

Archivos relevantes:

- `Producto/backendpanol/src/main/resources/application.yaml`
- `Producto/backendpanol/src/main/java/com/panol_project/backendpanol/bootstrap/config/SecurityConfig.java`
- `Producto/backendpanol/pom.xml`

### Prometheus

Prometheus ya está provisionado para scrapear el backend.

Configuración actual:

- job: `panol-backend`
- `metrics_path`: `/actuator/prometheus`
- target: `backend:8080`
- `scrape_interval`: `15s`
- receiver de remote write habilitado para `k6`

Archivo:

- `Producto/observability/prometheus/prometheus.yml`

### Grafana

Grafana está levantado con datasource provisionado hacia Prometheus.

Configuración actual:

- datasource por defecto: `Prometheus`
- URL: `http://prometheus:9090`
- acceso: `proxy`

Archivos:

- `Producto/observability/grafana/provisioning/datasources/prometheus.yaml`
- `Producto/observability/grafana/provisioning/dashboards/dashboard-provider.yaml`
- `Producto/observability/grafana/dashboards/panol-backend-overview.json`
- `Producto/observability/grafana/dashboards/panol-performance-testing.json`

Importante:

- hoy sí existen dashboards JSON provisionados desde el repositorio;
- Grafana los carga automáticamente dentro de la carpeta `Pañol Salud`.

### Performance testing

Existe una batería de scripts k6 funcional y organizada:

- `Producto/tests/load/k6/smoke-test.js`
- `Producto/tests/load/k6/flows/docente.js`
- `Producto/tests/load/k6/flows/coordinador.js`
- `Producto/tests/load/k6/flows/director.js`
- `Producto/tests/load/k6/scenarios/esc-02-load.js`
- `Producto/tests/load/k6/scenarios/esc-03-stress.js`
- `Producto/tests/load/k6/scenarios/esc-04-spike.js`
- `Producto/tests/load/k6/scenarios/esc-05-soak.js`

El documento de referencia de performance está en:

- `Producto/tests/load/PERFORMANCE_TESTING.md`

Los scripts ahora aceptan `BASE_URL` por variable de entorno, lo que permite ejecutarlos:

- desde el host, apuntando a `http://localhost:18080`;
- o desde contenedores Docker, apuntando a `http://panol-backend:8080`.

## 3. Topología local actual

### Puertos visibles

- Frontend: `http://localhost:18081`
- Backend: `http://localhost:18080`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`
- PostgreSQL local: `localhost:5432`

### Contenedores actuales

Los servicios levantados hoy son:

- `panol-backend`
- `panol-frontend`
- `panol-prometheus`
- `panol-grafana`
- `panol-postgres`

### Red Docker

La red compartida usada por el stack es `panol-local`.

## 4. Perfil de recursos de performance

Hay un override específico para pruebas de performance:

- `Producto/docker-compose.perf.yml`

Ese override aplica al backend:

- límite CPU: `1.0`
- reserva CPU: `0.5`
- límite memoria: `512M`
- reserva memoria: `256M`
- `SERVER_TOMCAT_THREADS_MAX=80`
- `SERVER_TOMCAT_THREADS_MIN_SPARE=10`

La intención de este perfil es aproximar un entorno acotado para validar comportamiento bajo carga sin depender de la nube.

## 5. Estado actual de datos de prueba

### Base local

La base local está pensada para usar:

- `APP_DB_ENV=docker`
- `DB_DOCKER_HOST=panol-postgres`
- `DB_DOCKER_USER=panol_user`
- `DB_DOCKER_NAME=panol`
- `DB_DOCKER_PASSWORD=Panol_2026_Duoc`

### Seed local

El seed actual es:

- `Producto/databasepanol/seeds/00_local_initial_flow_seed.sql`

Ese seed carga:

- roles;
- carreras;
- usuarios QA;
- categorías;
- ubicaciones;
- subjects;
- rooms;
- implementos;
- stock;
- individuales;
- movimientos de inventario;
- préstamos demo;
- historial de estados de préstamo.

### Usuarios QA actuales

Usuarios disponibles para login y pruebas:

| Rol | Nombre | RUT | Email | Password |
| --- | --- | --- | --- | --- |
| coordinador | Coordinador QA Local | `11111111-1` | `coordinador.local@panolsalud.test` | `Coord2026!` |
| docente | Docente QA Local | `22222222-2` | `docente.local@panolsalud.test` | `Docente2026!` |
| director | Director QA Local | `33333333-3` | `director.local@panolsalud.test` | `Director2026!` |

## 6. Qué se validó

### Validaciones funcionales

Se validó con k6 que:

- el login funciona para los 3 roles;
- `GET /api/v2/implements` responde correctamente;
- `GET /api/v2/loans?mine=true` funciona para docente;
- `GET /api/v2/loans?page=1&size=10` funciona para coordinador y director;
- `GET /api/v2/auth/me` responde autenticado.

### Validaciones de rendimiento

Se ejecutaron al menos pruebas cortas de smoke sobre el stack local reconstruido.

Resultado práctico:

- el backend respondió sin errores en las corridas de validación;
- el entorno local soportó los endpoints base sin fallas inmediatas;
- el perfil de performance quedó listo para usar con `k6`;
- Prometheus recibió métricas reales de `k6` vía remote write;
- Grafana quedó provisionando correctamente ambos dashboards por archivo.

## 7. Estado de Grafana

Grafana fue reseteado recientemente y quedó listo para volver a usar credenciales iniciales.

Acceso esperado:

- usuario: `admin`
- contraseña: `admin`

Si vuelve a fallar el login, el motivo más probable es que el volumen `producto_grafana-data` haya quedado persistido con otro usuario inicial.

## 8. Dashboards disponibles

Actualmente existen dos dashboards versionados:

- `Pañol Salud - Backend Overview`
- `Pañol Salud - Performance Testing`

### Backend Overview

Incluye:

- salud general del backend;
- requests por segundo;
- latencia p95;
- error rate;
- memoria JVM;
- CPU;
- threads JVM;
- GC pause;
- requests por endpoint;
- latencia p95 por endpoint;
- errores por endpoint;
- métricas de HikariCP.

### Performance Testing

Incluye:

- requests/s de `k6`;
- latencia p50, p95 y máxima de `k6`;
- error rate de `k6`;
- VUs activas;
- memoria JVM;
- CPU;
- threads;
- Hikari activas;
- tráfico `k6` por endpoint;
- latencia `k6` p95 por endpoint;
- latencia backend durante la prueba;
- errores y presión general del backend durante la ejecución.

## 9. Lo que todavía no está completo

Esto es lo que aún falta o conviene construir:

- alertas de Prometheus o Grafana;
- una rutina de arranque/documentación más corta para dejar el stack listo en un solo comando;
- una forma más formal de versionar dashboards adicionales por escenario;
- métricas directas de tiempo de consulta SQL si se decide instrumentarlas en backend;
- una forma estándar de conservar histórico largo de resultados de `k6` entre reinicios del entorno.

## 10. Comandos útiles actuales

### Levantar PostgreSQL local

```bash
cd Producto/databasepanol
docker compose up -d
```

### Levantar el stack normal

```bash
cd Producto
docker compose up -d --build
```

### Levantar el stack con límites de performance

```bash
cd Producto
docker compose -f docker-compose.yaml -f docker-compose.perf.yml --compatibility up -d --build
```

### Correr smoke con k6 desde contenedor

```bash
docker run --rm -i --network panol-local \
  -e BASE_URL=http://panol-backend:8080 \
  -e DOCENTE_RUT=22222222-2 \
  -e DOCENTE_PASSWORD=Docente2026! \
  grafana/k6 run --vus 1 --duration 15s - < tests/load/k6/smoke-test.js
```

### Correr smoke con k6 y enviar métricas a Prometheus

```bash
docker run --rm -i --network panol-local \
  -e BASE_URL=http://panol-backend:8080 \
  -e DOCENTE_RUT=22222222-2 \
  -e DOCENTE_PASSWORD=Docente2026! \
  -e K6_PROMETHEUS_RW_SERVER_URL=http://panol-prometheus:9090/api/v1/write \
  -e K6_PROMETHEUS_RW_TREND_STATS=p(50),p(95),max \
  grafana/k6 run --vus 1 --duration 15s -o experimental-prometheus-rw - \
  < tests/load/k6/smoke-test.js
```

## 11. Recomendación para la IA que continúe

Si una IA va a seguir desde aquí, la siguiente prioridad debería ser:

1. refinar los dashboards con thresholds y alertas reales por ambiente;
2. decidir si se quieren más métricas de base de datos a nivel consulta y no solo a nivel pool;
3. dejar un flujo simple de arranque y validación del stack;
4. documentar qué métricas exactas mirar en Grafana para decidir si el sistema va bien o no.
