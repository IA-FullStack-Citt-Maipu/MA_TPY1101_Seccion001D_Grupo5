from datetime import datetime, timezone

from app.agent.prompts import build_system_prompt
from app.tools import get_tools
from app.tools.alerts import listar_implementos_bajo_stock_minimo
from app.tools.analytics import (
    contar_prestamos_por_producto,
    recomendar_reposicion,
    resumen_inventario_por_categoria,
)
from app.tools.catalog import buscar_implementos
from app.tools.categories import listar_categorias
from app.tools.details import detalle_implemento
from app.tools.loans import listar_prestamos, listar_prestamos_programados
from app.tools.locations import listar_ubicaciones
from app.tools.stock import consultar_stock


def test_tools_registry_depends_on_role() -> None:
    coordinator_names = [tool.name for tool in get_tools("COORDINADOR")]
    director_names = [tool.name for tool in get_tools("DIRECTOR")]

    assert coordinator_names == [
        "buscar_implementos",
        "consultar_stock",
        "listar_prestamos",
        "listar_prestamos_programados",
        "listar_implementos_bajo_stock_minimo",
        "contar_prestamos_por_producto",
        "recomendar_reposicion",
        "resumen_inventario_por_categoria",
        "detalle_implemento",
        "listar_ubicaciones",
        "listar_categorias",
    ]
    assert director_names == [
        "buscar_implementos",
        "consultar_stock",
        "listar_prestamos",
        "listar_prestamos_programados",
        "listar_implementos_bajo_stock_minimo",
        "contar_prestamos_por_producto",
        "recomendar_reposicion",
        "resumen_inventario_por_categoria",
    ]
    assert "detalle_implemento" not in director_names
    assert "listar_ubicaciones" not in director_names
    assert "listar_categorias" not in director_names


def test_build_system_prompt_uses_backend_item_types_and_guardrails() -> None:
    prompt = build_system_prompt("DIRECTOR")
    assert "consumable" in prompt
    assert "reusable" in prompt
    assert "individual" in prompt
    assert "stock minimo" in prompt
    assert "prestamos programados" in prompt
    assert "requester_uuid" in prompt
    assert "performed_by" in prompt
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
    assert result["data"]["count"] == 10
    assert result["data"]["items"][0]["nombre"] == "item-0"
    assert "uuid" not in result["data"]["items"][0]
    assert result["presentation"]["ui_blocks"][0]["type"] == "entity_list"
    assert "technical" not in result["presentation"]


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
    ]

    monkeypatch.setattr(
        "app.tools.alerts.backend_client.get_safe",
        lambda *_args, **_kwargs: {"ok": True, "source": "backend", "data": payload},
    )
    result = listar_implementos_bajo_stock_minimo.invoke({})

    assert result["ok"] is True
    assert result["data"]["count"] == 1
    assert result["data"]["items"][0]["nombre"] == "Guantes"
    assert result["data"]["items"][0]["stock_gap"] == 2
    assert "technical" not in result["presentation"]


def test_consultar_stock_success_is_sanitized(monkeypatch) -> None:
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
        if path == "/api/v2/implements":
            assert params == {"name": "Microscopio"}
            return {"ok": True, "source": "backend", "data": [{"uuid": "impl-1", "name": "Microscopio"}]}
        if path == "/api/v2/implements/impl-1/stock":
            return {"ok": True, "source": "backend", "data": payload}
        raise AssertionError(f"Unexpected path: {path}")

    monkeypatch.setattr("app.tools.stock.backend_client.get_safe", fake_get_safe)
    result = consultar_stock.invoke({"implemento": "Microscopio"})

    assert result["ok"] is True
    assert result["data"]["stock"]["total_stock"] == 12
    assert result["data"]["individuals_count"] == 8
    assert result["data"]["implemento"] == "Microscopio"
    assert result["presentation"]["ui_blocks"][0]["type"] == "stat_group"
    assert "technical" not in result["presentation"]


def test_detalle_implemento_success_is_sanitized(monkeypatch) -> None:
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
        if path == "/api/v2/implements":
            assert params == {"name": "Microscopio"}
            return {"ok": True, "source": "backend", "data": [{"uuid": "impl-1", "name": "Microscopio"}]}
        if path == "/api/v2/implements/impl-1":
            return {"ok": True, "source": "backend", "data": payload}
        raise AssertionError(f"Unexpected path: {path}")

    monkeypatch.setattr("app.tools.details.backend_client.get_safe", fake_get_safe)
    result = detalle_implemento.invoke({"implemento": "Microscopio"})

    assert result["ok"] is True
    assert result["data"]["nombre"] == "Microscopio"
    assert result["data"]["item_type"] == "reusable"
    assert result["data"]["stock"]["min_stock"] == 3
    assert "recent_movements" not in result["data"]
    assert result["presentation"]["ui_blocks"][0]["type"] == "entity_list"
    assert "technical" not in result["presentation"]


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
    assert "uuid" not in result["data"]["items"][0]


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
    assert "uuid" not in result["data"]["items"][1]


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
                "items": [{"requested_quantity": 1, "reserved_quantity": 1, "delivered_quantity": 0}],
            },
            {
                "uuid": "loan-2",
                "status": "pending",
                "scheduled_at": "2026-05-26T10:00:00Z",
                "created_at": "2026-05-20T10:00:00Z",
                "requester_uuid": "user-2",
                "room": {"uuid": "room-1", "name": "Sala 101"},
                "subject": {"uuid": "subject-1", "name": "Anatomia"},
                "items": [{"requested_quantity": 2, "reserved_quantity": 1, "delivered_quantity": 0}],
            },
            {
                "uuid": "loan-3",
                "status": "prepared",
                "scheduled_at": "2026-05-26T10:00:00Z",
                "created_at": "2026-05-20T10:00:00Z",
                "requester_uuid": "user-1",
                "room": {"uuid": "room-1", "name": "Sala 101"},
                "subject": {"uuid": "subject-1", "name": "Anatomia"},
                "items": [{"requested_quantity": 3, "reserved_quantity": 3, "delivered_quantity": 0}],
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
            }
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

    result = listar_prestamos.invoke({"estado": "prepared", "limite": 3})
    assert result["ok"] is True
    assert result["data"]["count"] == 2
    assert result["data"]["pages_scanned"] == 2
    assert calls == [{"page": 1, "size": 3}, {"page": 2, "size": 3}]
    first = result["data"]["items"][0]
    assert first["status"] == "prepared"
    assert "requester_uuid" not in first
    assert "room" not in first
    assert "subject" not in first
    assert first["items_count"] == 1
    assert first["totals"] == {"requested": 3, "reserved": 3, "delivered": 0}


def test_listar_prestamos_invalid_estado_returns_structured_error() -> None:
    result = listar_prestamos.invoke({"estado": "unknown"})
    assert result["ok"] is False
    assert result["status_code"] == 400
    assert result["error_code"] == "LOAN_STATUS_INVALID"
    assert "prepared" in result["message"]


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


def test_contar_prestamos_por_producto_counts_unique_loans(monkeypatch) -> None:
    page_one = {
        "items": [
            {
                "uuid": "loan-1",
                "status": "approved",
                "scheduled_at": "2026-06-01T10:00:00Z",
                "items": [
                    {"implement_uuid": "impl-1", "implement_name": "Microscopio"},
                    {"implement_uuid": "impl-2", "implement_name": "Pinza"},
                ],
            },
            {
                "uuid": "loan-2",
                "status": "overdue",
                "scheduled_at": "2026-06-02T10:00:00Z",
                "items": [{"implement_uuid": "impl-1", "implement_name": "Microscopio"}],
            },
        ],
        "page": 1,
        "size": 100,
        "total_pages": 2,
        "has_next": True,
    }
    page_two = {
        "items": [
            {
                "uuid": "loan-3",
                "status": "completed",
                "scheduled_at": "2026-06-03T10:00:00Z",
                "items": [{"implement_uuid": "impl-3", "implement_name": "Camilla"}],
            },
            {
                "uuid": "loan-4",
                "status": "approved",
                "scheduled_at": "2026-06-04T10:00:00Z",
                "items": [
                    {"implement_uuid": "impl-1", "implement_name": "Microscopio"},
                    {"implement_uuid": "impl-1", "implement_name": "Microscopio"},
                ],
            },
        ],
        "page": 2,
        "size": 100,
        "total_pages": 2,
        "has_next": False,
    }

    def fake_get_safe(path, params=None):
        assert path == "/api/v2/loans"
        if params == {"page": 1, "size": 100}:
            return {"ok": True, "source": "backend", "data": page_one}
        if params == {"page": 2, "size": 100}:
            return {"ok": True, "source": "backend", "data": page_two}
        raise AssertionError(f"Unexpected params: {params}")

    def fake_get_safe_with_lookup(path, params=None):
        if path == "/api/v2/implements":
            assert params == {"name": "Microscopio"}
            return {"ok": True, "source": "backend", "data": [{"uuid": "impl-1", "name": "Microscopio"}]}
        return fake_get_safe(path, params=params)

    monkeypatch.setattr("app.tools.analytics.backend_client.get_safe", fake_get_safe_with_lookup)
    result = contar_prestamos_por_producto.invoke({"implemento": "Microscopio"})

    assert result["ok"] is True
    assert result["data"]["loan_count"] == 3
    assert result["data"]["implement_name"] == "Microscopio"
    assert result["data"]["status_breakdown"]["approved"] == 2
    assert result["data"]["status_breakdown"]["overdue"] == 1
    assert result["data"]["count_mode"] == "unique_loans"
    assert "technical" not in result["presentation"]


def test_recomendar_reposicion_uses_operational_rule(monkeypatch) -> None:
    class FixedDateTime(datetime):
        @classmethod
        def now(cls, tz=None):
            current = datetime(2026, 6, 22, 12, 0, 0, tzinfo=timezone.utc)
            return current if tz is None else current.astimezone(tz)

    implements_payload = [
        {
            "uuid": "impl-1",
            "name": "Guantes",
            "category": {"uuid": "cat-1", "name": "Bioseguridad"},
            "stock": {
                "total_stock": 20,
                "min_stock": 10,
                "available": 4,
                "reserved": 1,
                "loaned": 12,
                "damaged": 3,
            },
        },
        {
            "uuid": "impl-2",
            "name": "Microscopio",
            "category": {"uuid": "cat-2", "name": "Optica"},
            "stock": {
                "total_stock": 8,
                "min_stock": 2,
                "available": 3,
                "reserved": 1,
                "loaned": 4,
                "damaged": 0,
            },
        },
        {
            "uuid": "impl-3",
            "name": "Gasas",
            "category": {"uuid": "cat-1", "name": "Bioseguridad"},
            "stock": {
                "total_stock": 50,
                "min_stock": 5,
                "available": 7,
                "reserved": 0,
                "loaned": 43,
                "damaged": 0,
            },
        },
    ]
    loans_page = {
        "items": [
            {"uuid": "loan-1", "scheduled_at": "2026-06-10T10:00:00Z", "items": [{"implement_uuid": "impl-1"}]},
            {"uuid": "loan-2", "scheduled_at": "2026-06-11T10:00:00Z", "items": [{"implement_uuid": "impl-1"}]},
            {"uuid": "loan-3", "scheduled_at": "2026-06-12T10:00:00Z", "items": [{"implement_uuid": "impl-1"}]},
            {"uuid": "loan-4", "scheduled_at": "2026-06-13T10:00:00Z", "items": [{"implement_uuid": "impl-3"}]},
            {"uuid": "loan-5", "scheduled_at": "2026-06-14T10:00:00Z", "items": [{"implement_uuid": "impl-3"}]},
            {"uuid": "loan-6", "scheduled_at": "2026-06-15T10:00:00Z", "items": [{"implement_uuid": "impl-3"}]},
        ],
        "page": 1,
        "size": 100,
        "total_pages": 1,
        "has_next": False,
    }

    def fake_get_safe(path, params=None):
        if path == "/api/v2/implements":
            return {"ok": True, "source": "backend", "data": implements_payload}
        if path == "/api/v2/loans":
            assert params == {"page": 1, "size": 100}
            return {"ok": True, "source": "backend", "data": loans_page}
        raise AssertionError(f"Unexpected path: {path}")

    monkeypatch.setattr("app.tools.analytics.backend_client.get_safe", fake_get_safe)
    monkeypatch.setattr("app.tools.analytics.datetime", FixedDateTime)
    result = recomendar_reposicion.invoke({"limite": 5})

    assert result["ok"] is True
    assert result["data"]["count"] == 2
    first = result["data"]["items"][0]
    assert first["nombre"] == "Guantes"
    assert first["recommendation_level"] == "critical"
    assert "stock disponible bajo el minimo" in first["reasons"]
    assert result["data"]["items"][1]["nombre"] == "Gasas"


def test_resumen_inventario_por_categoria_groups_stock(monkeypatch) -> None:
    payload = [
        {
            "uuid": "impl-1",
            "name": "Guantes",
            "category": {"uuid": "cat-1", "name": "Bioseguridad"},
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
            "uuid": "impl-2",
            "name": "Gasas",
            "category": {"uuid": "cat-1", "name": "Bioseguridad"},
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
            "uuid": "impl-3",
            "name": "Microscopio",
            "category": {"uuid": "cat-2", "name": "Optica"},
            "stock": {
                "total_stock": 6,
                "min_stock": 1,
                "available": 4,
                "reserved": 0,
                "loaned": 2,
                "damaged": 0,
            },
        },
    ]

    monkeypatch.setattr(
        "app.tools.analytics.backend_client.get_safe",
        lambda *_args, **_kwargs: {"ok": True, "source": "backend", "data": payload},
    )
    result = resumen_inventario_por_categoria.invoke({"limite": 10})

    assert result["ok"] is True
    assert result["data"]["count"] == 2
    first = result["data"]["categories"][0]
    assert first["category_name"] == "Bioseguridad"
    assert first["implements_total"] == 2
    assert first["low_stock_items"] == 1
    assert first["stock"]["total_stock"] == 30


def test_identifier_request_exposes_technical_block_only_for_coordinator(monkeypatch) -> None:
    from app.client.context import set_query_intent, set_user_role

    payload = [
        {
            "uuid": "impl-1",
            "name": "Microscopio",
            "active": True,
            "available": True,
            "stock": {"total_stock": 2, "available": 1, "min_stock": 1},
        }
    ]

    monkeypatch.setattr(
        "app.tools.catalog.backend_client.get_safe",
        lambda *_args, **_kwargs: {"ok": True, "source": "backend", "data": payload},
    )
    set_user_role("COORDINADOR")
    set_query_intent("identifier_request")

    result = buscar_implementos.invoke({"nombre": "Microscopio"})

    assert result["presentation"]["technical"]["items"][0]["uuid"] == "impl-1"
