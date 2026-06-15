from __future__ import annotations

from prometheus_client import CONTENT_TYPE_LATEST, Counter, Histogram, generate_latest


BOT_HTTP_REQUESTS_TOTAL = Counter(
    "bot_http_requests_total",
    "Total de solicitudes HTTP procesadas por el bot.",
    ("path", "method", "status"),
)

BOT_HTTP_REQUEST_LATENCY_SECONDS = Histogram(
    "bot_http_request_latency_seconds",
    "Latencia HTTP en segundos por ruta y metodo.",
    ("path", "method"),
)

BOT_TOOL_CALLS_TOTAL = Counter(
    "bot_tool_calls_total",
    "Total de ejecuciones de tools.",
    ("tool", "outcome", "status_code"),
)

BOT_TOOL_LATENCY_SECONDS = Histogram(
    "bot_tool_latency_seconds",
    "Latencia de tools en segundos.",
    ("tool", "outcome"),
)

BOT_LLM_CALLS_TOTAL = Counter(
    "bot_llm_calls_total",
    "Total de llamadas al proveedor LLM.",
    ("model", "outcome"),
)

BOT_LLM_LATENCY_SECONDS = Histogram(
    "bot_llm_latency_seconds",
    "Latencia de llamadas al LLM en segundos.",
    ("model", "outcome"),
)


def record_http_request(path: str, method: str, status: int, latency_seconds: float) -> None:
    BOT_HTTP_REQUESTS_TOTAL.labels(path=path, method=method, status=str(status)).inc()
    BOT_HTTP_REQUEST_LATENCY_SECONDS.labels(path=path, method=method).observe(max(latency_seconds, 0.0))


def record_tool_call(tool_name: str, outcome: str, status_code: int, latency_seconds: float) -> None:
    BOT_TOOL_CALLS_TOTAL.labels(
        tool=tool_name,
        outcome=outcome,
        status_code=str(status_code),
    ).inc()
    BOT_TOOL_LATENCY_SECONDS.labels(tool=tool_name, outcome=outcome).observe(max(latency_seconds, 0.0))


def record_llm_call(model: str, outcome: str, latency_seconds: float) -> None:
    BOT_LLM_CALLS_TOTAL.labels(model=model, outcome=outcome).inc()
    BOT_LLM_LATENCY_SECONDS.labels(model=model, outcome=outcome).observe(max(latency_seconds, 0.0))


def render_metrics_payload() -> bytes:
    return generate_latest()


def metrics_content_type() -> str:
    return CONTENT_TYPE_LATEST
