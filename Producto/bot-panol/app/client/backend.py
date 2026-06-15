from typing import Any

from datetime import datetime, timezone
import httpx

from app.client.context import get_request_id, get_token
from app.config import settings
from app.observability.logger import log_event


class BackendClient:
    """HTTP client with JWT passthrough and structured error mapping."""

    def __init__(self) -> None:
        self.base_url = settings.BACKEND_BASE_URL.rstrip("/")

    def _auth_headers(self) -> dict[str, str]:
        token = get_token()
        request_id = get_request_id()
        headers = {
            "X-Client-Origin": "AI-Agent",
            "Content-Type": "application/json",
        }
        if request_id:
            headers["X-Request-ID"] = request_id
        if token:
            headers["Authorization"] = f"Bearer {token}"
        if settings.BACKEND_CLIENT_SECRET:
            headers["X-Client-Secret"] = settings.BACKEND_CLIENT_SECRET
        return headers

    def _full_url(self, path: str) -> str:
        if not path.startswith("/"):
            path = f"/{path}"
        return f"{self.base_url}{path}"

    def _structured_error_from_response(self, response: httpx.Response) -> dict[str, Any]:
        error_code = str(response.status_code)
        message = "No fue posible obtener la informacion desde backend."
        timestamp: str | None = None

        try:
            payload = response.json()
            if isinstance(payload, dict):
                error_code = str(payload.get("code", error_code))
                message = str(payload.get("message", message))
                raw_timestamp = payload.get("timestamp")
                timestamp = None if raw_timestamp is None else str(raw_timestamp)
        except Exception:
            pass

        return {
            "ok": False,
            "source": "backend",
            "status_code": response.status_code,
            "error_code": error_code,
            "message": message,
            "timestamp": timestamp,
        }

    def request_safe(
        self,
        method: str,
        path: str,
        *,
        params: dict[str, Any] | None = None,
        json_data: Any = None,
        data: Any = None,
        headers: dict[str, str] | None = None,
    ) -> dict[str, Any]:
        url = self._full_url(path)
        request_headers = self._auth_headers()
        if headers:
            request_headers.update(headers)

        max_attempts = settings.BACKEND_RETRY_COUNT + 1
        timeout = settings.BACKEND_TIMEOUT_SECONDS

        for attempt in range(1, max_attempts + 1):
            try:
                with httpx.Client(timeout=timeout) as client:
                    response = client.request(
                        method=method.upper(),
                        url=url,
                        params=params,
                        json=json_data,
                        data=data,
                        headers=request_headers,
                    )
                if response.is_error:
                    return self._structured_error_from_response(response)
                return {
                    "ok": True,
                    "source": "backend",
                    "data": response.json(),
                }
            except httpx.TimeoutException:
                if attempt < max_attempts:
                    continue
                return {
                    "ok": False,
                    "source": "backend",
                    "status_code": 504,
                    "error_code": "BACKEND_TIMEOUT",
                    "message": "El backend no respondio dentro del tiempo esperado.",
                    "timestamp": datetime.now(timezone.utc).isoformat(),
                }
            except httpx.RequestError:
                if attempt < max_attempts:
                    continue
                return {
                    "ok": False,
                    "source": "backend",
                    "status_code": 503,
                    "error_code": "BACKEND_UNREACHABLE",
                    "message": "No fue posible conectar con el backend.",
                    "timestamp": datetime.now(timezone.utc).isoformat(),
                }
            except Exception:
                log_event("backend_client_unexpected_error", level="ERROR", path=path, method=method)
                return {
                    "ok": False,
                    "source": "backend",
                    "status_code": 500,
                    "error_code": "BACKEND_CLIENT_ERROR",
                    "message": "Error inesperado en cliente backend.",
                    "timestamp": datetime.now(timezone.utc).isoformat(),
                }

        return {
            "ok": False,
            "source": "backend",
            "status_code": 500,
            "error_code": "BACKEND_CLIENT_ERROR",
            "message": "Error inesperado en cliente backend.",
            "timestamp": datetime.now(timezone.utc).isoformat(),
        }

    def get_safe(self, path: str, params: dict[str, Any] | None = None) -> dict[str, Any]:
        return self.request_safe("GET", path, params=params)


backend_client = BackendClient()
