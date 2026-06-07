import os
from uuid import uuid4

import httpx
import pytest
from fastapi.testclient import TestClient

from app.client.context import set_request_id, set_token
from app.main import app
from app.tools.catalog import buscar_implementos
from app.tools.loans import listar_prestamos
from app.tools.stock import consultar_stock


pytestmark = pytest.mark.integration


@pytest.fixture(scope="session")
def integration_config() -> dict[str, str]:
    config = {
        "backend_url": os.getenv("BOT_INT_BACKEND_URL", "").strip(),
        "rut": os.getenv("BOT_INT_RUT", "").strip(),
        "password": os.getenv("BOT_INT_PASSWORD", "").strip(),
    }
    missing = [name for name, value in config.items() if not value]
    if missing:
        pytest.skip(
            "Missing integration env vars: BOT_INT_BACKEND_URL, BOT_INT_RUT, BOT_INT_PASSWORD",
            allow_module_level=True,
        )
    return config


@pytest.fixture(scope="session")
def backend_token(integration_config: dict[str, str]) -> str:
    with httpx.Client(base_url=integration_config["backend_url"], timeout=20.0) as client:
        response = client.post(
            "/api/v2/auth/login",
            json={
                "rut": integration_config["rut"],
                "password": integration_config["password"],
            },
        )
    assert response.status_code == 200
    body = response.json()
    assert isinstance(body.get("accessToken"), str)
    return body["accessToken"]


@pytest.fixture(autouse=True)
def tool_context(integration_config: dict[str, str], backend_token: str) -> None:
    set_token(backend_token)
    set_request_id(str(uuid4()))
    from app.client.backend import backend_client

    backend_client.base_url = integration_config["backend_url"].rstrip("/")


def test_integration_login_token_is_usable(integration_config: dict[str, str], backend_token: str) -> None:
    with httpx.Client(base_url=integration_config["backend_url"], timeout=20.0) as client:
        response = client.get(
            "/api/v2/implements",
            headers={"Authorization": f"Bearer {backend_token}"},
        )
    assert response.status_code == 200
    assert isinstance(response.json(), list)


def test_integration_buscar_implementos_contract() -> None:
    result = buscar_implementos.invoke({"nombre": "a"})
    assert result["source"] == "backend"
    assert "ok" in result
    if result["ok"]:
        assert isinstance(result["data"]["items"], list)
        if result["data"]["items"]:
            assert "tipo" not in result["data"]["items"][0]
    else:
        assert "status_code" in result
        assert "error_code" in result


def test_integration_consultar_stock_contract() -> None:
    search = buscar_implementos.invoke({"nombre": "a"})
    if not search.get("ok"):
        pytest.skip("No se pudo obtener implementos reales para probar consultar_stock")
    items = search["data"].get("items", [])
    if not items:
        pytest.skip("No hay implementos disponibles para probar consultar_stock")

    implement_uuid = items[0].get("uuid")
    if not isinstance(implement_uuid, str) or not implement_uuid:
        pytest.skip("Implemento sin uuid util para test de stock")

    result = consultar_stock.invoke({"implement_uuid": implement_uuid})
    assert result["source"] == "backend"
    assert "ok" in result
    if result["ok"]:
        assert "stock" in result["data"]
    else:
        assert "status_code" in result


def test_integration_listar_prestamos_contract() -> None:
    result = listar_prestamos.invoke({"limite": 5})
    assert result["source"] == "backend"
    assert "ok" in result
    if result["ok"]:
        assert isinstance(result["data"]["items"], list)
        assert result["data"]["count"] <= 5
        assert isinstance(result["data"]["pages_scanned"], int)
        assert result["data"]["pages_scanned"] >= 1
    else:
        assert "status_code" in result
        assert "error_code" in result


def test_integration_chat_rejects_invalid_token() -> None:
    client = TestClient(app)
    response = client.post(
        "/api/v1/chat",
        headers={"Authorization": "Bearer invalid.token.value"},
        json={"message": "hola"},
    )
    assert response.status_code == 401
    assert response.json()["detail"] == "TOKEN_INVALID"
