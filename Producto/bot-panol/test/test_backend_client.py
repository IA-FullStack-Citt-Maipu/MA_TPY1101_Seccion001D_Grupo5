import httpx

from app.client.backend import BackendClient
from app.client.context import set_request_id, set_token
from app.config import settings


def test_request_safe_adds_auth_and_request_id_headers(monkeypatch) -> None:
    captured: dict[str, object] = {}

    class FakeResponse:
        is_error = False

        @staticmethod
        def json():
            return {"ok": "yes"}

    class FakeClient:
        def __init__(self, timeout):
            captured["timeout"] = timeout

        def __enter__(self):
            return self

        def __exit__(self, exc_type, exc, tb):
            return False

        def request(self, method, url, params=None, json=None, data=None, headers=None):
            captured["method"] = method
            captured["url"] = url
            captured["params"] = params
            captured["headers"] = headers or {}
            return FakeResponse()

    monkeypatch.setattr("app.client.backend.httpx.Client", FakeClient)
    monkeypatch.setattr(settings, "BACKEND_BASE_URL", "http://backend:8080")
    monkeypatch.setattr(settings, "BACKEND_CLIENT_SECRET", "shared-secret")
    monkeypatch.setattr(settings, "BACKEND_TIMEOUT_SECONDS", 3.0)

    set_token("abc")
    set_request_id("req-123")
    client = BackendClient()
    result = client.request_safe("GET", "/api/v2/implements", params={"name": "gasa"})

    assert result["ok"] is True
    assert captured["method"] == "GET"
    assert captured["url"] == "http://backend:8080/api/v2/implements"
    assert captured["params"] == {"name": "gasa"}
    headers = captured["headers"]
    assert isinstance(headers, dict)
    assert headers["Authorization"] == "Bearer abc"
    assert headers["X-Request-ID"] == "req-123"
    assert headers["X-Client-Secret"] == "shared-secret"


def test_request_safe_timeout_returns_structured_error(monkeypatch) -> None:
    class FakeClient:
        def __init__(self, timeout):
            self.timeout = timeout

        def __enter__(self):
            return self

        def __exit__(self, exc_type, exc, tb):
            return False

        def request(self, *args, **kwargs):
            raise httpx.TimeoutException("timeout")

    monkeypatch.setattr("app.client.backend.httpx.Client", FakeClient)
    monkeypatch.setattr(settings, "BACKEND_RETRY_COUNT", 0)
    client = BackendClient()
    result = client.request_safe("GET", "/api/v2/implements")

    assert result["ok"] is False
    assert result["status_code"] == 504
    assert result["error_code"] == "BACKEND_TIMEOUT"
