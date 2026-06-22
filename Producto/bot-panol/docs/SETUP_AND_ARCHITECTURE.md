# Setup And Architecture

## Proposito

`bot-panol` es el microservicio del asistente de IA de Panol Salud. Expone una API
HTTP basada en FastAPI y utiliza LangGraph para orquestar consultas al backend de
inventario y generar respuestas contextuales con Gemini.

## Capacidades vigentes del asistente

El bot esta orientado exclusivamente a consultas de lectura y actualmente puede:

- identificar implementos bajo stock minimo;
- listar prestamos programados para hoy o para una fecha especifica;
- consultar disponibilidad y distribucion de stock de un implemento;
- listar prestamos vencidos (`overdue`);
- contar cuantos prestamos historicos ha tenido un producto;
- recomendar reposicion con una regla operativa simple;
- resumir el inventario agrupado por categoria.

No implementa la HU-72 de ranking semestral.

## Requisitos Previos

- Docker Desktop o Docker Engine con Compose habilitado.
- Una API Key valida de Google Gemini (`GOOGLE_API_KEY`).
- El backend principal de Panol debe estar disponible dentro del mismo `docker compose`.

## Variables de Entorno

| Variable | Obligatoria | Descripcion | Ejemplo |
| --- | --- | --- | --- |
| `GOOGLE_API_KEY` | Si | API key usada por Gemini para responder consultas. | `your_google_api_key_here` |
| `GEMINI_MODEL` | No | Modelo Gemini usado por LangChain. | `gemini-2.5-flash-lite` |
| `LLM_TIMEOUT_SECONDS` | No | Timeout maximo de llamadas al modelo. | `20` |
| `BACKEND_BASE_URL` | Si | Base URL del backend principal consumido por las tools del bot. En Docker local apunta al servicio `backend`. | `http://backend:8080` |
| `BACKEND_CLIENT_SECRET` | Si | Secreto compartido enviado en `X-Client-Secret` para autorizar llamadas internas al backend. | `your_backend_ai_agent_secret_here` |
| `BACKEND_TIMEOUT_SECONDS` | No | Timeout maximo de llamadas HTTP del bot al backend. | `10` |
| `BACKEND_RETRY_COUNT` | No | Numero de reintentos HTTP hacia el backend ante errores transitorios. | `0` |
| `BOT_SECRET_KEY` | No | Fallback legacy para validar JWT si `JWT_SECRET_KEY` no esta definido. Preferir `JWT_SECRET_KEY`. | `your_bot_fallback_secret_here` |
| `JWT_SECRET_KEY` | Si | Secreto HS256 usado para validar los JWT emitidos por `backendpanol`. Debe coincidir con `APP_AUTH_JWT_SECRET`. | `your_backend_jwt_secret_here` |
| `JWT_ISSUER` | Si | Issuer esperado en el JWT. Debe coincidir con `APP_AUTH_JWT_ISSUER`. | `panol-backend` |
| `JWT_AUDIENCE` | No | Audience esperado del token puente emitido por backend para el bot. El valor recomendado y default actual es `bot-panol`. | `bot-panol` |
| `JWT_LEEWAY_SECONDS` | No | Tolerancia en segundos para validaciones de tiempo del JWT. | `30` |
| `MAX_ITERATIONS` | No | Limite de recursion del grafo LangGraph. | `10` |
| `MAX_HISTORY_MESSAGES` | No | Cantidad maxima de mensajes previos que se reinyectan en el contexto. | `20` |
| `METRICS_ENABLED` | No | Habilita o deshabilita el endpoint de metricas. | `true` |
| `LOG_LEVEL` | No | Nivel de logging del microservicio. | `INFO` |
| `CORS_ALLOWED_ORIGINS` | No | Lista separada por comas de origenes permitidos para el frontend. Se suma a los origenes locales por defecto. | `http://localhost:18081,http://127.0.0.1:18081` |

## Ejecucion Local

El entrypoint local recomendado es el `docker-compose.yaml` de `Producto/`.

1. Copiar `Producto/bot-panol/.env.example` a `Producto/bot-panol/.env`.
2. Completar al menos:
   - `GOOGLE_API_KEY`
   - `BACKEND_CLIENT_SECRET`
   - `JWT_SECRET_KEY`
   - `JWT_ISSUER`
   - `JWT_AUDIENCE` si necesitas sobreescribir el default `bot-panol`
3. Desde la carpeta `Producto/`, levantar el stack completo:

```bash
docker compose up --build -d
```

Esto levanta:

- `panol-backend` en `http://localhost:18080`
- `panol-frontend` en `http://localhost:18081`
- `panol-bot` en `http://localhost:18082`

El servicio `bot` toma parte de su configuracion desde:

- `Producto/bot-panol/.env`
- `Producto/.env` para valores compartidos del stack cuando aplica

## Endpoints Principales

### `POST /api/v1/chat`

Endpoint principal del asistente. Requiere header
`Authorization: Bearer <token-puente>`.

Contrato vigente:

- el frontend autentica la sesion web usando cookies HTTP-only del backend;
- luego solicita `POST /api/v2/auth/me/bot-token`;
- el backend emite un JWT efimero con `aud = bot-panol` y `token_use = bot-panol`;
- el frontend envia ese token puente al bot en el header `Authorization`;
- el bot valida firma, `iss`, `aud`, `token_use`, expiracion y rol (`COORDINADOR` o
  `DIRECTOR`).

El bot no depende de leer JWT desde `localStorage` ni `sessionStorage`.

### Politica conversacional vigente

- El bot bloquea antes del LLM solicitudes de escritura, cambios de estado o
  intentos de bypass de permisos.
- `COORDINADOR` recibe lectura operativa controlada.
- `DIRECTOR` recibe solo resumenes agregados y ejecutivos.
- El bot no expone `requester_uuid`, `performed_by`, `notes`, `asset_code` ni
  trazabilidad fina hacia el modelo ni en la respuesta final.

Request:

```json
{
  "message": "Consultar stock de jeringas",
  "conversation_id": "optional-conversation-id",
  "history": [
    { "role": "user", "content": "consulta previa" },
    { "role": "assistant", "content": "respuesta previa" }
  ]
}
```

Response:

```json
{
  "response": "Texto generado por el asistente",
  "conversation_id": "uuid",
  "tools_used": ["buscar_implementos"]
}
```

### `GET /health`

Healthcheck simple para validacion operativa del contenedor.

Response:

```json
{
  "status": "ok",
  "service": "bot-panol",
  "version": "0.1.0"
}
```
