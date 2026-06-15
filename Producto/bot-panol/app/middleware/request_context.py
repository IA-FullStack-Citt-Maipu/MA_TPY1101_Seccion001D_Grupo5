from __future__ import annotations

from time import perf_counter
from uuid import uuid4

from fastapi import Request
from starlette.middleware.base import BaseHTTPMiddleware

from app.client.context import set_request_id, set_token
from app.observability.logger import log_event
from app.observability.metrics import record_http_request


class RequestContextMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):  # type: ignore[no-untyped-def]
        request_id = request.headers.get("X-Request-ID", str(uuid4()))
        set_request_id(request_id)
        set_token("")

        started_at = perf_counter()
        response = None
        status_code = 500
        try:
            response = await call_next(request)
            status_code = response.status_code
            response.headers["X-Request-ID"] = request_id
            return response
        except Exception:
            log_event(
                "http_request_failed",
                level="ERROR",
                request_id=request_id,
                path=request.url.path,
                method=request.method,
                status_code=500,
            )
            raise
        finally:
            latency = perf_counter() - started_at
            record_http_request(
                path=request.url.path,
                method=request.method,
                status=status_code,
                latency_seconds=latency,
            )
            log_event(
                "http_request_completed",
                request_id=request_id,
                path=request.url.path,
                method=request.method,
                status_code=status_code,
                latency_ms=round(latency * 1000, 2),
            )
