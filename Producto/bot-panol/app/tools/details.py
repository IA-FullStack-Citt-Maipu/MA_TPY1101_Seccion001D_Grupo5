from __future__ import annotations

from time import perf_counter
from typing import Any

from langchain_core.tools import tool

from app.client.backend import backend_client
from app.observability.metrics import record_tool_call
from app.tools.common import build_entity_list_block, build_stat_group_block, build_tool_output, resolve_implement


@tool
def detalle_implemento(implemento: str) -> dict[str, Any]:
    """
    Obtiene el detalle seguro de un implemento a partir de su nombre o identificador tecnico.
    """
    started_at = perf_counter()
    resolved, error = resolve_implement(implemento)
    if error is not None:
        status_code = int(error.get("status_code", 500))
        record_tool_call("detalle_implemento", "error", status_code, perf_counter() - started_at)
        return error

    implement_uuid = str(resolved.get("uuid") or "").strip()
    result = backend_client.get_safe(f"/api/v2/implements/{implement_uuid}")
    if not result.get("ok"):
        status_code = int(result.get("status_code", 500))
        record_tool_call("detalle_implemento", "error", status_code, perf_counter() - started_at)
        return result

    payload = result.get("data", {})
    if not isinstance(payload, dict):
        payload = {}

    category = payload.get("category")
    if not isinstance(category, dict):
        category = {}

    location = payload.get("location")
    if not isinstance(location, dict):
        location = {}

    stock = payload.get("stock")
    if not isinstance(stock, dict):
        stock = {}

    output = build_tool_output(
        data={
            "nombre": payload.get("name"),
            "descripcion": payload.get("description"),
            "item_type": payload.get("item_type"),
            "activo": payload.get("active"),
            "display_location": payload.get("display_location"),
            "categoria": {
                "nombre": category.get("name"),
                "activa": category.get("active"),
            }
            if category
            else None,
            "ubicacion": {
                "nombre": location.get("name"),
                "descripcion": location.get("description"),
            }
            if location
            else None,
            "stock": {
                "total_stock": stock.get("total_stock"),
                "available": stock.get("available"),
                "reserved": stock.get("reserved"),
                "loaned": stock.get("loaned"),
                "damaged": stock.get("damaged"),
                "min_stock": payload.get("min_stock"),
            },
        },
        summary=f"Detalle operativo disponible para {payload.get('name') or 'el implemento consultado'}.",
        ui_blocks=[
            build_entity_list_block(
                "Detalle del implemento",
                [
                    {
                        "title": payload.get("name"),
                        "subtitle": category.get("name") or "Sin categoria",
                        "meta": [
                            f"Ubicacion visible: {payload.get('display_location') or 'No disponible'}",
                            f"Tipo: {payload.get('item_type') or 'No informado'}",
                            f"Estado: {'Activo' if payload.get('active') else 'Inactivo'}",
                        ],
                        "badges": [],
                    }
                ],
            ),
            build_stat_group_block(
                "Resumen de stock",
                [
                    {"label": "Disponible", "value": str(stock.get("available") or 0)},
                    {"label": "Reservado", "value": str(stock.get("reserved") or 0)},
                    {"label": "Prestado", "value": str(stock.get("loaned") or 0)},
                    {"label": "Daniado", "value": str(stock.get("damaged") or 0)},
                ],
            ),
        ],
        technical={"implement_uuid": payload.get("uuid"), "nombre": payload.get("name")},
    )
    record_tool_call("detalle_implemento", "success", 200, perf_counter() - started_at)
    return output
