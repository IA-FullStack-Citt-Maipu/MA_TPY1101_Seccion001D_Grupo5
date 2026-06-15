from app.agent.prompts import build_system_prompt
from app.tools import get_tools
from app.tools.alerts import listar_implementos_bajo_stock_minimo
from app.tools.catalog import buscar_implementos
from app.tools.categories import listar_categorias
from app.tools.details import detalle_implemento
from app.tools.loans import listar_prestamos, listar_prestamos_programados
from app.tools.locations import listar_ubicaciones
from app.tools.stock import consultar_stock


def test_tools_registry_contains_phase_one_tools() -> None:
    names = [tool.name for tool in get_tools()]
    assert names == [
        "buscar_implementos",
        "consultar_stock",
        "listar_prestamos",
        "listar_prestamos_programados",
        "detalle_implemento",
        "listar_ubicaciones",
        "listar_categorias",
        "listar_implementos_bajo_stock_minimo",
    ]


def test_build_system_prompt_uses_backend_item_types() -> None:
    prompt = build_system_prompt("COORDINADOR")
    assert "consumable" in prompt
    assert "reusable" in prompt
    assert "individual" in prompt
    assert "stock minimo" in prompt
    assert "prestamos programados" in prompt
    assert "fungible" not in prompt
    assert "no_fungible" not in prompt


def test_buscar_implementos_success_truncates_top_10(monkeypatch) -> None:
    payload = [
        {
            "uuid": f"uuid-{idx}",
            "name": f"item-{idx}",
            "active": True,
            "available": True,
            "stock": {"total_stock": idx},
        }
        for idx in range(15)
    ]

    def fake_get_safe(path, params=None):
        assert path == "/api/v2/implements"
        assert params == {"name": "gasa"}
        return {"ok": True, "source": "backend", "data": payload}

    monkeypatch.setattr("app.tools.catalog.backend_client.get_safe", fake_get_safe)

    result = buscar_implementos.invoke({"nombre": "gasa"})
    assert result["ok"] is True
    assert result["source"] == "backend"
    assert result["data"]["count"] == 10
    assert len(result["data"]["items"]) == 10
    assert result["data"]["items"][0]["uuid"] == "uuid-0"
    assert result["data"]["items"][-1]["uuid"] == "uuid-9"
    assert "tipo" not in result["data"]["items"][0]


def test_buscar_implementos_error_passthrough(monkeypatch) -> None:
    expected = {
        "ok": False,
        "source": "backend",
        "status_code": 403,
        "error_code": "403",
        "message": "Acceso denegado",
        "timestamp": "2026-05-26T00:00:00Z",
    }

    monkeypatch.setattr("app.tools.catalog.backend_client.get_safe", lambda *_args, **_kwargs: expected)
    result = buscar_implementos.invoke({"nombre": "gasa"})
    assert result == expected


def test_listar_implementos_bajo_stock_minimo_success(monkeypatch) -> None:
    payload = [
        {
            "uuid": "uuid-1",
            "name": "Guantes",
            "active": True,
            "available": True,
            "stock": {
                "total_stock": 10,
                "min_stock": 5,
                "available": 3,
                "reserved": 1,
                "loaned": 5,
                "damaged": 1,
            },
        },
        {
            "uuid": "uuid-2",
            "name": "Gasas",
            "active": True,
            "available": True,
            "stock": {
                "total_stock": 20,
                "min_stock": 4,
                "available": 8,
                "reserved": 2,
                "loaned": 8,
                "damaged": 2,
            },
        },
        {
            "uuid": "uuid-3",
            "name": "Inactivo",
            "active": False,
            "available": True,
            "stock": {
                "total_stock": 5,
                "min_stock": 2,
                "available": 0,
                "reserved": 0,
                "loaned": 5,
                "damaged": 0,
            },
        },
    ]

    def fake_get_safe(path, params=None):
        assert path == "/api/v2/implements"
        assert params is None
        return {"ok": True, "source": "backend", "data": payload}

    monkeypatch.setattr("app.tools.alerts.backend_client.get_safe", fake_get_safe)
    result = listar_implementos_bajo_stock_minimo.invoke({})

    assert result["ok"] is True
    assert result["data"]["count"] == 1
    assert result["data"]["items"][0]["uuid"] == "uuid-1"
    assert result["data"]["items"][0]["stock_gap"] == 2


def test_listar_implementos_bajo_stock_minimo_error_passthrough(monkeypatch) -> None:
    expected = {
        "ok": False,
        "source": "backend",
        "status_code": 500,
        "error_code": "BACKEND_CLIENT_ERROR",
        "message": "Error interno del cliente backend",
        "timestamp": None,
    }
    monkeypatch.setattr("app.tools.alerts.backend_client.get_safe", lambda *_args, **_kwargs: expected)
    result = listar_implementos_bajo_stock_minimo.invoke({})
    assert result == expected


def test_consultar_stock_success_includes_preview(monkeypatch) -> None:
    payload = {
        "implement_uuid": "impl-1",
        "item_type": "individual",
        "stock": {
            "total_stock": 12,
            "min_stock": 2,
            "available": 6,
            "reserved": 2,
            "loaned": 3,
            "damaged": 1,
        },
        "individuals": [
            {"uuid": f"ind-{idx}", "asset_code": f"A-{idx}", "status": "available", "condition": "ok"}
            for idx in range(8)
        ],
    }

    def fake_get_safe(path, params=None):
        assert path == "/api/v2/implements/impl-1/stock"
        assert params is None
        return {"ok": True, "source": "backend", "data": payload}

    monkeypatch.setattr("app.tools.stock.backend_client.get_safe", fake_get_safe)
    result = consultar_stock.invoke({"implement_uuid": "impl-1"})

    assert result["ok"] is True
    assert result["data"]["stock"]["total_stock"] == 12
    assert result["data"]["individuals_count"] == 8
    assert len(result["data"]["individuals_preview"]) == 5
    assert result["data"]["individuals_preview"][0]["uuid"] == "ind-0"


def test_detalle_implemento_success(monkeypatch) -> None:
    payload = {
        "uuid": "impl-1",
        "name": "Microscopio",
        "description": "Microscopio binocular",
        "item_type": "reusable",
        "active": True,
        "display_location": "Laboratorio 1",
        "category": {"uuid": "cat-1", "name": "Optica", "active": True},
        "location": {"uuid": "loc-1", "name": "Bodega", "description": "Bodega principal"},
        "min_stock": 3,
        "stock": {
            "total_stock": 10,
            "available": 7,
            "reserved": 1,
            "loaned": 2,
            "damaged": 0,
        },
        "recent_movements": [
            {
                "action": "stock_in",
                "quantity": 2,
                "performed_by": "Usuario Test",
                "timestamp": "2026-06-07T12:00:00Z",
                "notes": "Ingreso inicial",
            }
        ],
    }

    def fake_get_safe(path, params=None):
        assert path == "/api/v2/implements/impl-1"
        assert params is None
        return {"ok": True, "source": "backend", "data": payload}

    monkeypatch.setattr("app.tools.details.backend_client.get_safe", fake_get_safe)
    result = detalle_implemento.invoke({"implement_uuid": "impl-1"})

    assert result["ok"] is True
    assert result["data"]["uuid"] == "impl-1"
    assert result["data"]["item_type"] == "reusable"
    assert result["data"]["stock"]["min_stock"] == 3
    assert result["data"]["recent_movements"][0]["performed_by"] == "Usuario Test"


def test_detalle_implemento_error_passthrough(monkeypatch) -> None:
    expected = {
        "ok": False,
        "source": "backend",
        "status_code": 404,
        "error_code": "IMPLEMENT_NOT_FOUND",
        "message": "Implemento no encontrado",
        "timestamp": "2026-06-07T00:00:00Z",
    }
    monkeypatch.setattr("app.tools.details.backend_client.get_safe", lambda *_args, **_kwargs: expected)
    result = detalle_implemento.invoke({"implement_uuid": "missing"})
    assert result == expected


def test_consultar_stock_error_passthrough(monkeypatch) -> None:
    expected = {
        "ok": False,
        "source": "backend",
        "status_code": 404,
        "error_code": "IMPLEMENT_NOT_FOUND",
        "message": "Implemento no encontrado",
        "timestamp": "2026-05-26T00:00:00Z",
    }
    monkeypatch.setattr("app.tools.stock.backend_client.get_safe", lambda *_args, **_kwargs: expected)
    result = consultar_stock.invoke({"implement_uuid": "missing"})
    assert result == expected


def test_listar_ubicaciones_success(monkeypatch) -> None:
    payload = [
        {"uuid": "loc-1", "name": "Bodega", "description": "Principal", "active": True},
        {"uuid": "loc-2", "name": "Sala 101", "description": "Primer piso", "active": False},
    ]

    monkeypatch.setattr(
        "app.tools.locations.backend_client.get_safe",
        lambda *_args, **_kwargs: {"ok": True, "source": "backend", "data": payload},
    )
    result = listar_ubicaciones.invoke({})

    assert result["ok"] is True
    assert result["data"]["count"] == 2
    assert result["data"]["items"][0]["nombre"] == "Bodega"


def test_listar_ubicaciones_error_passthrough(monkeypatch) -> None:
    expected = {
        "ok": False,
        "source": "backend",
        "status_code": 503,
        "error_code": "BACKEND_UNREACHABLE",
        "message": "Backend no disponible",
        "timestamp": None,
    }
    monkeypatch.setattr("app.tools.locations.backend_client.get_safe", lambda *_args, **_kwargs: expected)
    result = listar_ubicaciones.invoke({})
    assert result == expected


def test_listar_categorias_success(monkeypatch) -> None:
    payload = [
        {"uuid": "cat-1", "name": "Bioseguridad"},
        {"uuid": "cat-2", "name": "Optica"},
    ]

    monkeypatch.setattr(
        "app.tools.categories.backend_client.get_safe",
        lambda *_args, **_kwargs: {"ok": True, "source": "backend", "data": payload},
    )
    result = listar_categorias.invoke({})

    assert result["ok"] is True
    assert result["data"]["count"] == 2
    assert result["data"]["items"][1]["nombre"] == "Optica"


def test_listar_categorias_error_passthrough(monkeypatch) -> None:
    expected = {
        "ok": False,
        "source": "backend",
        "status_code": 403,
        "error_code": "ACCESS_DENIED",
        "message": "Acceso denegado",
        "timestamp": "2026-06-07T00:00:00Z",
    }
    monkeypatch.setattr("app.tools.categories.backend_client.get_safe", lambda *_args, **_kwargs: expected)
    result = listar_categorias.invoke({})
    assert result == expected


def test_listar_prestamos_success_filters_and_limits_across_pages(monkeypatch) -> None:
    page_one = {
        "items": [
            {
                "uuid": "loan-1",
                "status": "approved",
                "scheduled_at": "2026-05-26T10:00:00Z",
                "created_at": "2026-05-20T10:00:00Z",
                "requester_uuid": "user-1",
                "room": {"uuid": "room-1", "name": "Sala 101"},
                "subject": {"uuid": "subject-1", "name": "Anatomia"},
                "items": [
                    {"requested_quantity": 1, "reserved_quantity": 1, "delivered_quantity": 0},
                ],
            },
            {
                "uuid": "loan-2",
                "status": "pending",
                "scheduled_at": "2026-05-26T10:00:00Z",
                "created_at": "2026-05-20T10:00:00Z",
                "requester_uuid": "user-2",
                "room": {"uuid": "room-1", "name": "Sala 101"},
                "subject": {"uuid": "subject-1", "name": "Anatomia"},
                "items": [
                    {"requested_quantity": 2, "reserved_quantity": 1, "delivered_quantity": 0},
                ],
            },
            {
                "uuid": "loan-3",
                "status": "prepared",
                "scheduled_at": "2026-05-26T10:00:00Z",
                "created_at": "2026-05-20T10:00:00Z",
                "requester_uuid": "user-1",
                "room": {"uuid": "room-1", "name": "Sala 101"},
                "subject": {"uuid": "subject-1", "name": "Anatomia"},
                "items": [
                    {"requested_quantity": 3, "reserved_quantity": 3, "delivered_quantity": 0},
                ],
            },
        ],
        "page": 1,
        "size": 3,
        "total_items": 6,
        "total_pages": 2,
        "has_next": True,
        "has_previous": False,
    }
    page_two = {
        "items": [
            {
                "uuid": "loan-4",
                "status": "prepared",
                "scheduled_at": "2026-05-27T10:00:00Z",
                "created_at": "2026-05-21T10:00:00Z",
                "requester_uuid": "user-1",
                "room": {"uuid": "room-2", "name": "Sala 102"},
                "subject": {"uuid": "subject-2", "name": "Biologia"},
                "items": [
                    {"requested_quantity": 2, "reserved_quantity": 2, "delivered_quantity": 1},
                    {"requested_quantity": 1, "reserved_quantity": 1, "delivered_quantity": 1},
                ],
            },
            {
                "uuid": "loan-5",
                "status": "overdue",
                "scheduled_at": "2026-05-27T10:00:00Z",
                "created_at": "2026-05-21T10:00:00Z",
                "requester_uuid": "user-1",
                "room": {"uuid": "room-2", "name": "Sala 102"},
                "subject": {"uuid": "subject-2", "name": "Biologia"},
                "items": [
                    {"requested_quantity": 5, "reserved_quantity": 4, "delivered_quantity": 4},
                ],
            },
        ],
        "page": 2,
        "size": 3,
        "total_items": 6,
        "total_pages": 2,
        "has_next": False,
        "has_previous": True,
    }

    calls: list[dict[str, object] | None] = []

    def fake_get_safe(path, params=None):
        assert path == "/api/v2/loans"
        calls.append(params)
        if params == {"page": 1, "size": 3}:
            return {"ok": True, "source": "backend", "data": page_one}
        if params == {"page": 2, "size": 3}:
            return {"ok": True, "source": "backend", "data": page_two}
        raise AssertionError(f"Unexpected params: {params}")

    monkeypatch.setattr("app.tools.loans.backend_client.get_safe", fake_get_safe)

    result = listar_prestamos.invoke({"estado": "prepared", "requester_uuid": "user-1", "limite": 3})
    assert result["ok"] is True
    assert result["data"]["count"] == 2
    assert result["data"]["pages_scanned"] == 2
    assert calls == [{"page": 1, "size": 3}, {"page": 2, "size": 3}]
    first = result["data"]["items"][0]
    assert first["status"] == "prepared"
    assert first["requester_uuid"] == "user-1"
    assert first["items_count"] == 1
    assert first["totals"] == {"requested": 3, "reserved": 3, "delivered": 0}
    assert result["data"]["items"][-1]["status"] == "prepared"


def test_listar_prestamos_stops_when_backend_has_no_next_page(monkeypatch) -> None:
    payload = {
        "items": [
            {
                "uuid": "loan-1",
                "status": "overdue",
                "scheduled_at": "2026-05-26T10:00:00Z",
                "created_at": "2026-05-20T10:00:00Z",
                "requester_uuid": "user-1",
                "room": {"uuid": "room-1", "name": "Sala 101"},
                "subject": {"uuid": "subject-1", "name": "Anatomia"},
                "items": [
                    {"requested_quantity": 2, "reserved_quantity": 1, "delivered_quantity": 1},
                ],
            }
        ],
        "page": 1,
        "size": 5,
        "total_items": 1,
        "total_pages": 1,
        "has_next": False,
        "has_previous": False,
    }

    def fake_get_safe(path, params=None):
        assert path == "/api/v2/loans"
        assert params == {"page": 1, "size": 5}
        return {"ok": True, "source": "backend", "data": payload}

    monkeypatch.setattr("app.tools.loans.backend_client.get_safe", fake_get_safe)

    result = listar_prestamos.invoke({"estado": "overdue", "limite": 5})
    assert result["ok"] is True
    assert result["data"]["count"] == 1
    assert result["data"]["pages_scanned"] == 1
    assert result["data"]["items"][0]["status"] == "overdue"


def test_listar_prestamos_invalid_estado_returns_structured_error() -> None:
    result = listar_prestamos.invoke({"estado": "unknown"})
    assert result["ok"] is False
    assert result["status_code"] == 400
    assert result["error_code"] == "LOAN_STATUS_INVALID"
    assert "prepared" in result["message"]
    assert "overdue" in result["message"]


def test_listar_prestamos_error_passthrough(monkeypatch) -> None:
    expected = {
        "ok": False,
        "source": "backend",
        "status_code": 401,
        "error_code": "401",
        "message": "No autorizado",
        "timestamp": "2026-05-26T00:00:00Z",
    }
    monkeypatch.setattr("app.tools.loans.backend_client.get_safe", lambda *_args, **_kwargs: expected)
    result = listar_prestamos.invoke({})
    assert result == expected


def test_listar_prestamos_programados_success_filters_by_date(monkeypatch) -> None:
    page_one = {
        "items": [
            {
                "uuid": "loan-1",
                "status": "approved",
                "scheduled_at": "2026-06-07T10:00:00Z",
                "created_at": "2026-06-01T10:00:00Z",
                "requester_uuid": "user-1",
                "room": {"uuid": "room-1", "name": "Sala 101"},
                "subject": {"uuid": "subject-1", "name": "Anatomia"},
                "items": [{"requested_quantity": 1, "reserved_quantity": 1, "delivered_quantity": 0}],
            },
            {
                "uuid": "loan-2",
                "status": "pending",
                "scheduled_at": "2026-06-08T10:00:00Z",
                "created_at": "2026-06-01T10:00:00Z",
                "requester_uuid": "user-2",
                "room": {"uuid": "room-1", "name": "Sala 101"},
                "subject": {"uuid": "subject-1", "name": "Anatomia"},
                "items": [{"requested_quantity": 2, "reserved_quantity": 1, "delivered_quantity": 0}],
            },
        ],
        "page": 1,
        "size": 3,
        "total_items": 3,
        "total_pages": 2,
        "has_next": True,
        "has_previous": False,
    }
    page_two = {
        "items": [
            {
                "uuid": "loan-3",
                "status": "prepared",
                "scheduled_at": "2026-06-07T15:00:00-04:00",
                "created_at": "2026-06-02T10:00:00Z",
                "requester_uuid": "user-3",
                "room": {"uuid": "room-2", "name": "Sala 102"},
                "subject": {"uuid": "subject-2", "name": "Biologia"},
                "items": [{"requested_quantity": 3, "reserved_quantity": 3, "delivered_quantity": 0}],
            }
        ],
        "page": 2,
        "size": 3,
        "total_items": 3,
        "total_pages": 2,
        "has_next": False,
        "has_previous": True,
    }

    def fake_get_safe(path, params=None):
        assert path == "/api/v2/loans"
        if params == {"page": 1, "size": 3}:
            return {"ok": True, "source": "backend", "data": page_one}
        if params == {"page": 2, "size": 3}:
            return {"ok": True, "source": "backend", "data": page_two}
        raise AssertionError(f"Unexpected params: {params}")

    monkeypatch.setattr("app.tools.loans.backend_client.get_safe", fake_get_safe)

    result = listar_prestamos_programados.invoke({"fecha": "2026-06-07", "limite": 3})
    assert result["ok"] is True
    assert result["data"]["count"] == 2
    assert result["data"]["filters"]["fecha"] == "2026-06-07"
    assert result["data"]["pages_scanned"] == 2
    assert result["data"]["items"][0]["uuid"] == "loan-1"
    assert result["data"]["items"][1]["uuid"] == "loan-3"


def test_listar_prestamos_programados_invalid_date_returns_structured_error() -> None:
    result = listar_prestamos_programados.invoke({"fecha": "07-06-2026"})
    assert result["ok"] is False
    assert result["status_code"] == 400
    assert result["error_code"] == "LOAN_DATE_INVALID"
    assert "YYYY-MM-DD" in result["message"]
