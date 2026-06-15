# Auditoría OWASP - Seguridad `staging-perf`

## Información general
- Ambiente: `staging-perf` desplegado en GCP Cloud Run
- Frontend auditado: `https://panol-frontend-staging-perf-369729778197.us-central1.run.app`
- Backend auditado: `https://panol-backend-staging-perf-369729778197.us-central1.run.app`
- Fecha de ejecución: `2026-06-15`
- Alcance: inyección, control de acceso/RLS, manipulación de JWT y controles criptográficos/sesión

## Resumen ejecutivo
| Caso | Categoría OWASP | Estado | Resultado |
|---|---|---|---|
| `CP-SEG-INJECTION-001` | A03 Injection | Pasó | Los payloads fueron tratados como texto; no hubo errores SQL ni fuga de datos |
| `CP-SEG-RLS-001` | A01 Broken Access Control | Pasó | Un docente no pudo leer ni listar un préstamo ajeno |
| `CP-SEG-JWT-001` | A07 Identification and Authentication Failures | Pasó | Tokens manipulados fueron rechazados con `401` |
| `CP-SEG-CRYPTO-001` | A02 Cryptographic Failures | Pasó con observaciones | HTTPS y cookies seguras correctas; faltan headers de hardening como `HSTS` y `CSP` |

## Evidencia frontend generada
- Captura login: `evidencias/02-seguridad/capturas/frontend-staging-perf-login.png`
- Captura dashboard docente: `evidencias/02-seguridad/capturas/frontend-staging-perf-dashboard-docente.png`
- Consola navegador: `evidencias/02-seguridad/capturas/frontend-staging-perf-console.json`
- Seguridad de red desde navegador: `evidencias/02-seguridad/capturas/frontend-staging-perf-network-security.json`
- Snapshot de storage del navegador: `evidencias/02-seguridad/capturas/frontend-staging-perf-storage-snapshot.json`
- Fetch frontend a préstamo ajeno: `evidencias/02-seguridad/capturas/frontend-staging-perf-foreign-loan-fetch.json`
- Fetch frontend a `/auth/me`: `evidencias/02-seguridad/capturas/frontend-staging-perf-auth-me.json`

## CP-SEG-INJECTION-001 - SQL Injection en búsqueda de implementos
- Estado: Pasó
- Herramienta usada: `requests` contra backend desplegado
- Endpoint: `GET /api/v2/implements?name=...`
- Objetivo: validar que el filtro `name` no ejecute payloads SQL ni exponga errores internos
- Resultado esperado: respuesta controlada, sin stacktrace SQL y sin retorno de tablas ajenas
- Resultado obtenido: los cuatro payloads devolvieron `200` con respuesta vacía o controlada; no hubo mensajes de `PostgreSQL`, `JDBC`, `SQLSTATE` ni trazas internas
- Evidencia:
  - `evidencias/02-seguridad/capturas/staging-perf-injection-results.json`
- Diagnóstico: el endpoint resistió los payloads probados a nivel de consulta simple
- Recomendación: mantener consultas parametrizadas y agregar pruebas automáticas de inyección en CI para filtros nuevos

## CP-SEG-RLS-001 - Broken Access Control / RLS / IDOR
- Estado: Pasó
- Herramienta usada: `requests` y navegador real sobre frontend desplegado
- Flujo validado:
  - se confirmó o creó un `Docente B` de prueba
  - `Docente B` generó un préstamo propio en `staging-perf`
  - `Docente A` intentó leer `GET /api/v2/loans/{uuid-ajeno}`
  - `Docente A` consultó su listado `mine=true`
  - desde el frontend autenticado de `Docente A` se repitió el fetch al préstamo ajeno
- Resultado esperado: el préstamo ajeno no debe ser visible ni recuperable
- Resultado obtenido:
  - backend directo: `404 LOAN_NOT_FOUND`
  - listado propio: el UUID ajeno no aparece
  - frontend autenticado: `foreign-loan-fetch status=404`
- Evidencia:
  - `evidencias/02-seguridad/capturas/staging-perf-rls-user-b-setup.json`
  - `evidencias/02-seguridad/capturas/staging-perf-rls-idor-results.json`
  - `evidencias/02-seguridad/capturas/frontend-staging-perf-foreign-loan-fetch.json`
  - `evidencias/02-seguridad/capturas/frontend-staging-perf-console.json`
  - `evidencias/02-seguridad/capturas/frontend-staging-perf-dashboard-docente.png`
- Diagnóstico: el aislamiento por usuario se comportó correctamente; no hubo exposición horizontal de préstamos
- Recomendación: agregar un caso automatizado de regresión para acceso horizontal por UUID en préstamos y futuros módulos sensibles

## CP-SEG-JWT-001 - JWT manipulado
- Estado: Pasó
- Herramienta usada: `requests`
- Objetivo: validar rechazo de token con `role` alterado y token con `alg=none`
- Resultado esperado: `401` y sin escalada de privilegios
- Resultado obtenido:
  - token con `role=DIRECTOR` pero firma inconsistente: `401`
  - token con `alg=none`: `401`
- Evidencia:
  - `evidencias/02-seguridad/capturas/staging-perf-jwt-tamper-results.json`
  - `evidencias/02-seguridad/capturas/staging-perf-crypto-auth-session.json`
- Diagnóstico: el backend no aceptó tokens adulterados ni permitió abuso del claim `role`
- Recomendación: mantener este caso dentro de una suite de smoke de seguridad en cada despliegue

## CP-SEG-CRYPTO-001 - Cifrado de transporte, hash de contraseñas y sesión
- Estado: Pasó con observaciones
- Herramientas usadas: `requests`, navegador real y revisión de código
- Objetivo: validar controles criptográficos y de sesión visibles desde el despliegue

### Validaciones positivas
- Backend y frontend expuestos por `HTTPS`
- Login devuelve cookies de sesión con:
  - `Secure`
  - `HttpOnly`
  - `SameSite=None`
- El JWT observado usa `HS256`
- En frontend no se encontraron tokens en `localStorage` ni `sessionStorage`
- El backend usa `BCrypt` para hash/verificación de contraseñas

### Evidencia
- Runtime:
  - `evidencias/02-seguridad/capturas/staging-perf-crypto-auth-session.json`
  - `evidencias/02-seguridad/capturas/frontend-staging-perf-network-security.json`
  - `evidencias/02-seguridad/capturas/frontend-staging-perf-storage-snapshot.json`
  - `evidencias/02-seguridad/capturas/staging-perf-security-headers.json`
- Código:
  - `Producto/backendpanol/src/main/java/com/panol_project/backendpanol/modules/auth/api/AuthCookieService.java` líneas `36`, `44`, `70-72`
  - `Producto/backendpanol/src/main/java/com/panol_project/backendpanol/modules/auth/application/AuthService.java` líneas `50`, `104`, `307`, `316`, `320`
  - `Producto/backendpanol/src/main/java/com/panol_project/backendpanol/modules/users/application/UserAdminService.java` línea `61`

### Observaciones
- El frontend guarda `auth_user` en `localStorage`. No contiene token, pero sí metadata de usuario y rol.
- En las respuestas capturadas no se observó `Strict-Transport-Security`.
- Tampoco se observó `Content-Security-Policy` en frontend o backend.

### Diagnóstico
- Los controles base de sesión y autenticación son correctos para `staging-perf`.
- La principal deuda no está en el cifrado de credenciales o cookies, sino en headers de hardening del despliegue.

### Recomendación
1. Agregar `Strict-Transport-Security` en frontend y backend expuestos por HTTPS.
2. Definir una `Content-Security-Policy` explícita para reducir superficie ante XSS.
3. Evaluar si `auth_user` necesita persistir en `localStorage` o puede resolverse desde memoria/sesión.

## Hallazgos y observaciones finales
| ID | Severidad | Estado | Descripción |
|---|---|---|---|
| `OBS-SEG-001` | Media | Abierto | No se observó header `Strict-Transport-Security` en las respuestas auditadas |
| `OBS-SEG-002` | Media | Abierto | No se observó `Content-Security-Policy` en frontend ni backend |
| `OBS-SEG-003` | Baja | Abierto | El frontend persiste `auth_user` en `localStorage`; no expone token, pero sí metadata de usuario |

## Diagnóstico general
- Resultado global: `Apto con observaciones`
- Conclusión: los controles evaluados de inyección, RLS/IDOR y JWT resistieron correctamente en `staging-perf`. La prioridad de mejora está en hardening HTTP del despliegue y en reducir metadata persistida en cliente.
