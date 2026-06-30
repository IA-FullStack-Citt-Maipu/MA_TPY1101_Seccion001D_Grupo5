from __future__ import annotations

from time import perf_counter
from typing import Any

from langchain_core.tools import tool

from app.client.backend import backend_client
from app.observability.metrics import record_tool_call
from app.tools.common import build_entity_list_block, build_tool_output


@tool
def buscar_implementos(nombre: str) -> dict[str, Any]:
    """
    Busca implementos por nombre y devuelve un resumen seguro de hasta 10 resultados.
    """
    started_at = perf_counter()
    result = backend_client.get_safe("/api/v2/implements", params={"name": nombre})
    if not result.get("ok"):
        status_code = int(result.get("status_code", 500))
        record_tool_call("buscar_implementos", "error", status_code, perf_counter() - started_at)
        return result

    payload = result.get("data", [])
    if not isinstance(payload, list):
        payload = []

    items: list[dict[str, Any]] = []
    technical_items: list[dict[str, str | None]] = []
    entities: list[dict[str, Any]] = []

    for item in payload[:10]:
        if not isinstance(item, dict):
            continue

        stock = item.get("stock")
        if not isinstance(stock, dict):
            stock = {}
        category = item.get("category")
        if not isinstance(category, dict):
            category = {}
        location = item.get("location")
        if not isinstance(location, dict):
            location = {}

        safe_item = {
            "nombre": item.get("name"),
            "activo": item.get("active"),
            "disponible": item.get("available"),
            "categoria": category.get("name"),
            "ubicacion": location.get("name"),
            "stock": {
                "total_stock": stock.get("total_stock"),
                "min_stock": stock.get("min_stock"),
                "available": stock.get("available"),
                "reserved": stock.get("reserved"),
                "loaned": stock.get("loaned"),
                "damaged": stock.get("damaged"),
            },
        }
        items.append(safe_item)
        technical_items.append({"nombre": str(item.get("name") or ""), "uuid": item.get("uuid")})

        badges = []
        badges.append("Disponible" if item.get("available") else "Sin disponibilidad")
        badges.append("Activo" if item.get("active") else "Inactivo")
        entities.append(
            {
                "title": item.get("name"),
                "subtitle": category.get("name") or "Sin categoria",
                "meta": [
                    f"Ubicacion: {location.get('name') or 'No visible'}",
                    f"Stock disponible: {stock.get('available') or 0}",
                    f"Stock minimo: {stock.get('min_stock') or 0}",
                ],
                "badges": badges,
            }
        )

    output = build_tool_output(
        data={
            "query": nombre,
            "count": len(items),
            "items": items,
        },
        summary=f"Se encontraron {len(items)} implementos relacionados con '{nombre}'.",
        ui_blocks=[build_entity_list_block("Implementos encontrados", entities)] if entities else [],
        technical={"items": technical_items},
    )
    record_tool_call("buscar_implementos", "success", 200, perf_counter() - started_at)
    return output
