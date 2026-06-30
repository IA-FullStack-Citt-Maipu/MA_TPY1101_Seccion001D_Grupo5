from __future__ import annotations

from time import perf_counter
from typing import Any

from langchain_core.tools import tool

from app.client.backend import backend_client
from app.observability.metrics import record_tool_call
from app.tools.common import build_entity_list_block, build_tool_output


@tool
def listar_ubicaciones() -> dict[str, Any]:
    """
    Lista las ubicaciones disponibles en el backend.
    """
    started_at = perf_counter()
    result = backend_client.get_safe("/api/v2/locations")
    if not result.get("ok"):
        status_code = int(result.get("status_code", 500))
        record_tool_call("listar_ubicaciones", "error", status_code, perf_counter() - started_at)
        return result

    payload = result.get("data", [])
    if not isinstance(payload, list):
        payload = []

    items: list[dict[str, Any]] = []
    technical_items: list[dict[str, Any]] = []
    entities: list[dict[str, Any]] = []

    for row in payload:
        if not isinstance(row, dict):
            continue
        items.append(
            {
                "nombre": row.get("name"),
                "descripcion": row.get("description"),
                "activa": row.get("active"),
            }
        )
        technical_items.append({"nombre": row.get("name"), "uuid": row.get("uuid")})
        entities.append(
            {
                "title": row.get("name"),
                "subtitle": row.get("description"),
                "meta": [],
                "badges": ["Activa" if row.get("active") else "Inactiva"],
            }
        )

    output = build_tool_output(
        data={"count": len(items), "items": items},
        summary=f"Se listaron {len(items)} ubicaciones.",
        ui_blocks=[build_entity_list_block("Ubicaciones", entities)] if entities else [],
        technical={"items": technical_items},
    )
    record_tool_call("listar_ubicaciones", "success", 200, perf_counter() - started_at)
    return output
