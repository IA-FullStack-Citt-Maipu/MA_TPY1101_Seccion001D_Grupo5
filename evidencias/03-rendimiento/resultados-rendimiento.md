# Resultados de Rendimiento

## CP-REND-ESC-01 - Smoke Test sobre `staging-perf`
- Estado: Pasó
- Ambiente objetivo: `staging-perf` desplegado en GCP Cloud Run
- Backend probado: `https://panol-backend-staging-perf-369729778197.us-central1.run.app`
- Observabilidad usada: Prometheus local en `http://localhost:9090` scrapeando `panol-backend-staging-perf-369729778197.us-central1.run.app`
- Script ejecutado: `Producto/tests/load/k6/smoke-test.js`
- Fecha de ejecución: 2026-06-15

### Configuración usada
- VUs: `3`
- Duración: `1 minuto`
- Flujo: `login -> implements -> loans?mine=true -> auth/me -> logout`
- Credenciales QA: docente de `staging-perf`

### Evidencia generada
- Resumen k6: `evidencias/03-rendimiento/reportes-k6/esc-01-smoke-staging-perf-summary.json`
- Serie temporal k6: `evidencias/03-rendimiento/reportes-k6/esc-01-smoke-staging-perf-timeseries.json`
- Consola k6: `evidencias/03-rendimiento/reportes-k6/esc-01-smoke-staging-perf-console.log`
- Snapshot Prometheus: `evidencias/03-rendimiento/capturas/prometheus-esc-01-staging-perf-snapshot.json`

### Resultado esperado
- `p95 < 500 ms`
- `error rate < 0.5%`
- Sin errores HTTP `5xx`

### Resultado obtenido
- `checks`: `205/205` OK
- `http_req_failed`: `0.00%`
- `http_req_duration avg`: `199.86 ms`
- `http_req_duration p95`: `245.65 ms`
- `http_req_duration max`: `638.14 ms`
- `http_reqs`: `155`
- `throughput k6`: `2.50 req/s`
- `iterations`: `51`

### Señales observadas en Prometheus
- Memoria residente pico backend en ventana de `10m`: `435343360 bytes` (`81.09%` de `512 MiB`)
- Hikari activas pico en `10m`: `1`
- Hikari pendientes pico en `10m`: `0`
- Timeouts Hikari en `10m`: `0`
- Endpoints con tráfico visible:
  - `GET /api/v2/auth/me`
  - `GET /api/v2/implements`
  - `GET /api/v2/loans`
  - `POST /api/v2/auth/login`
  - `POST /api/v2/auth/logout`

### Diagnóstico
- El smoke sobre el backend desplegado pasó sin errores funcionales ni HTTP `5xx`.
- La latencia observada es sana para un smoke: `p95 245.65 ms`, bastante bajo el umbral de `500 ms`.
- El pool Hikari no mostró presión: sin espera y sin timeouts. Para esta carga, la base no es cuello de botella.
- La memoria del proceso sigue relativamente alta para una prueba tan pequeña, alrededor de `81%` del límite de instancia. No implica falla inmediata, pero sí confirma que el contenedor parte con una huella alta y conviene vigilarla antes de subir a `ESC-02`.
- La métrica de RPS en Prometheus queda más baja que la de k6 porque Prometheus promedia en una ventana temporal posterior al cierre del smoke. Para throughput de esta ejecución, el valor confiable es el de k6: `2.50 req/s`.
- La métrica de CPU del dashboard no se usó para este diagnóstico porque en este entorno sigue reportando valores no representativos.

### Recomendación
- El entorno `staging-perf` está apto para avanzar a la siguiente prueba de carga.
- Antes de `ESC-02`, conviene observar en Grafana la memoria del contenedor y confirmar que no siga creciendo sin volver a bajar entre corridas.
