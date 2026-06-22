from datetime import datetime, timedelta, timezone

import jwt
from fastapi.testclient import TestClient
from langchain_core.messages import AIMessage, HumanMessage, ToolMessage

from app.agent.nodes import LLMServiceUnavailableError
from app.config import settings
from app.main import app


def _make_signed_jwt(
    *,
    role: str = "COORDINADOR",
    subject: str = "11111111-1111-1111-1111-111111111111",
    issuer: str = "panol-backend",
    audience: str = "bot-panol",
    secret: str = "test-secret-key-with-at-least-32-bytes",
    expires_in_seconds: int = 3600,
    token_use: str = "bot-panol",
) -> str:
    now = datetime.now(timezone.utc)
    payload = {
        "iss": issuer,
        "sub": subject,
        "role": role,
        "aud": audience,
        "iat": int(now.timestamp()),
        "nbf": int(now.timestamp()),
        "exp": int((now + timedelta(seconds=expires_in_seconds)).timestamp()),
        "token_use": token_use,
    }
    return jwt.encode(payload, secret, algorithm="HS256")


def _configure_jwt_settings(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    monkeypatch.setattr(settings, "JWT_SECRET_KEY", "test-secret-key-with-at-least-32-bytes")
    monkeypatch.setattr(settings, "BOT_SECRET_KEY", "")
    monkeypatch.setattr(settings, "JWT_ISSUER", "panol-backend")
    monkeypatch.setattr(settings, "JWT_AUDIENCE", "bot-panol")
    monkeypatch.setattr(settings, "JWT_LEEWAY_SECONDS", 1)


def test_health_returns_expected_contract() -> None:
    client = TestClient(app)
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {
        "status": "ok",
        "service": "bot-panol",
        "version": "0.1.0",
    }


def test_metrics_endpoint_returns_prometheus_payload(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    monkeypatch.setattr(settings, "METRICS_ENABLED", True)
    client = TestClient(app)
    response = client.get("/metrics")

    assert response.status_code == 200
    assert "bot_http_requests_total" in response.text


def test_chat_without_authorization_returns_401() -> None:
    client = TestClient(app)
    response = client.post("/api/v1/chat", json={"message": "hola"})

    assert response.status_code == 401
    assert response.json() == {
        "detail": "TOKEN_MISSING",
        "message": "Se requiere un token de autorizacion valido.",
    }


def test_chat_preflight_returns_cors_headers() -> None:
    client = TestClient(app)
    response = client.options(
        "/api/v1/chat",
        headers={
            "Origin": "http://localhost:18081",
            "Access-Control-Request-Method": "POST",
            "Access-Control-Request-Headers": "authorization,content-type",
        },
    )

    assert response.status_code == 200
    assert response.headers["access-control-allow-origin"] == "http://localhost:18081"
    assert "POST" in response.headers["access-control-allow-methods"]


def test_chat_with_invalid_token_returns_401(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    _configure_jwt_settings(monkeypatch)
    client = TestClient(app)
    response = client.post(
        "/api/v1/chat",
        headers={"Authorization": "Bearer invalid.token.here"},
        json={"message": "hola"},
    )

    assert response.status_code == 401
    assert response.json() == {
        "detail": "TOKEN_INVALID",
        "message": "Se requiere un token de autorizacion valido.",
    }


def test_chat_with_wrong_token_use_returns_401(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    _configure_jwt_settings(monkeypatch)
    client = TestClient(app)
    token = _make_signed_jwt(token_use="web-access")

    response = client.post(
        "/api/v1/chat",
        headers={"Authorization": f"Bearer {token}"},
        json={"message": "hola"},
    )

    assert response.status_code == 401
    assert response.json() == {
        "detail": "TOKEN_INVALID",
        "message": "Se requiere un token de autorizacion valido.",
    }


def test_chat_with_disallowed_role_returns_403(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    _configure_jwt_settings(monkeypatch)
    client = TestClient(app)
    token = _make_signed_jwt(role="DOCENTE")

    response = client.post(
        "/api/v1/chat",
        headers={"Authorization": f"Bearer {token}"},
        json={
            "message": "Hola",
            "history": [{"role": "user", "content": "consulta inicial"}],
        },
    )

    assert response.status_code == 403
    assert response.json() == {
        "detail": "ROLE_NOT_ALLOWED",
        "message": "No tienes permisos para usar este servicio.",
    }


def test_chat_with_allowed_role_returns_tools_used_in_execution_order(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    _configure_jwt_settings(monkeypatch)
    client = TestClient(app)
    token = _make_signed_jwt(role="COORDINADOR")

    class FakeGraph:
        def invoke(self, state, config=None):
            return {
                "messages": [
                    HumanMessage(content="hola"),
                    AIMessage(content="", tool_calls=[{"name": "buscar_implementos", "args": {"nombre": "gasa"}, "id": "1", "type": "tool_call"}]),
                    ToolMessage(content="{}", name="buscar_implementos", tool_call_id="1"),
                    AIMessage(content="", tool_calls=[{"name": "consultar_stock", "args": {"implement_uuid": "abc"}, "id": "2", "type": "tool_call"}]),
                    ToolMessage(content="{}", name="consultar_stock", tool_call_id="2"),
                    ToolMessage(content="{}", name="consultar_stock", tool_call_id="3"),
                    AIMessage(content="respuesta final"),
                ],
                "conversation_id": state["conversation_id"],
            }

    monkeypatch.setattr("app.api.v1.chat.get_graph", lambda _role=None: FakeGraph())

    response = client.post(
        "/api/v1/chat",
        headers={"Authorization": f"Bearer {token}"},
        json={
            "message": "Hola",
            "history": [{"role": "user", "content": "consulta inicial"}],
        },
    )

    body = response.json()
    assert response.status_code == 200
    assert body["response"] == "respuesta final"
    assert isinstance(body["conversation_id"], str)
    assert body["tools_used"] == ["buscar_implementos", "consultar_stock", "consultar_stock"]


def test_chat_returns_503_when_llm_is_unavailable(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    _configure_jwt_settings(monkeypatch)
    client = TestClient(app)
    token = _make_signed_jwt(role="DIRECTOR")

    class FailingGraph:
        def invoke(self, state, config=None):
            raise LLMServiceUnavailableError("provider down")

    monkeypatch.setattr("app.api.v1.chat.get_graph", lambda _role=None: FailingGraph())

    response = client.post(
        "/api/v1/chat",
        headers={"Authorization": f"Bearer {token}"},
        json={"message": "Hola"},
    )

    assert response.status_code == 503
    assert response.json() == {
        "detail": "LLM_UNAVAILABLE",
        "message": "El servicio de IA no esta disponible temporalmente.",
    }


def test_chat_blocks_write_requests_before_invoking_graph(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    _configure_jwt_settings(monkeypatch)
    client = TestClient(app)
    token = _make_signed_jwt(role="COORDINADOR")

    class UnexpectedGraph:
        def invoke(self, state, config=None):
            raise AssertionError("graph should not be invoked")

    monkeypatch.setattr("app.api.v1.chat.get_graph", lambda _role=None: UnexpectedGraph())

    response = client.post(
        "/api/v1/chat",
        headers={"Authorization": f"Bearer {token}"},
        json={"message": "Aprueba este prestamo ahora mismo"},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["tools_used"] == []
    assert "solo puedo ayudarte con consultas de lectura" in body["response"].lower()


def test_chat_blocks_sensitive_traceability_requests_for_director(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    _configure_jwt_settings(monkeypatch)
    client = TestClient(app)
    token = _make_signed_jwt(role="DIRECTOR")

    class UnexpectedGraph:
        def invoke(self, state, config=None):
            raise AssertionError("graph should not be invoked")

    monkeypatch.setattr("app.api.v1.chat.get_graph", lambda _role=None: UnexpectedGraph())

    response = client.post(
        "/api/v1/chat",
        headers={"Authorization": f"Bearer {token}"},
        json={"message": "Quien movio este implemento y que notas internas dejo?"},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["tools_used"] == []
    assert "datos sensibles" in body["response"].lower()
