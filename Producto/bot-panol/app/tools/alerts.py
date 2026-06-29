from __future__ import annotations

from time import perf_counter
from typing import Any

from langchain_core.tools import tool

from app.client.backend import backend_client
from app.observability.metrics import record_tool_call
from app.tools.common import build_entity_list_block, build_tool_output, safe_int


@tool
def listar_implementos_bajo_stock_minimo() -> dict[str, Any]:
    """
    Lista implementos activos cuyo stock disponible esta por debajo del stock minimo.
    """
    started_at = perf_counter()
    result = backend_client.get_safe("/api/v2/implements")
    if not result.get("ok"):
        status_code = int(result.get("status_code", 500))
        record_tool_call("listar_implementos_bajo_stock_minimo", "error", status_code, perf_counter() - started_at)
        return result

    payload = result.get("data", [])
    if not isinstance(payload, list):
        payload = []

    items: list[dict[str, Any]] = []
    technical_items: list[dict[str, Any]] = []
    entities: list[dict[str, Any]] = []

    for item in payload:
        if not isinstance(item, dict):
            continue

        stock = item.get("stock")
        if not isinstance(stock, dict):
            stock = {}

        active = bool(item.get("active"))
        min_stock = safe_int(stock.get("min_stock"))
        available = safe_int(stock.get("available"))
        if not active or min_stock <= 0 or available >= min_stock:
            continue

        stock_gap = min_stock - available
        safe_item = {
            "nombre": item.get("name"),
            "activo": active,
            "stock": {
                "total_stock": stock.get("total_stock"),
                "min_stock": stock.get("min_stock"),
                "available": stock.get("available"),
                "reserved": stock.get("reserved"),
                "loaned": stock.get("loaned"),
                "damaged": stock.get("damaged"),
            },
            "stock_gap": stock_gap,
        }
        items.append(safe_item)
        technical_items.append({"nombre": item.get("name"), "uuid": item.get("uuid")})
        entities.append(
            {
                "title": item.get("name"),
                "subtitle": "Bajo stock minimo",
                "meta": [
                    f"Disponible: {available}",
                    f"Minimo: {min_stock}",
                    f"Brecha: {stock_gap}",
                ],
                "badges": ["Alerta operativa"],
            }
        )

    output = build_tool_output(
        data={"count": len(items), "items": items},
        summary=f"Hay {len(items)} implementos bajo stock minimo.",
        ui_blocks=[build_entity_list_block("Alertas de stock minimo", entities)] if entities else [],
        technical={"items": technical_items},
    )
    record_tool_call("listar_implementos_bajo_stock_minimo", "success", 200, perf_counter() - started_at)
    return output
