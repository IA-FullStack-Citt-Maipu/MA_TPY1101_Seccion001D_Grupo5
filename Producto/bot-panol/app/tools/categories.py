from time import perf_counter
from typing import Any

from langchain_core.tools import tool

from app.client.backend import backend_client
from app.observability.metrics import record_tool_call


@tool
def listar_categorias() -> dict[str, Any]:
    """
    Lista las categorias activas disponibles en el backend.
    """
    started_at = perf_counter()
    result = backend_client.get_safe("/api/v2/categories/active")
    if not result.get("ok"):
        status_code = int(result.get("status_code", 500))
        record_tool_call("listar_categorias", "error", status_code, perf_counter() - started_at)
        return result

    payload = result.get("data", [])
    if not isinstance(payload, list):
        payload = []

    items: list[dict[str, Any]] = []
    for row in payload:
        if not isinstance(row, dict):
            continue
        items.append(
            {
                "uuid": row.get("uuid"),
                "nombre": row.get("name"),
            }
        )

    output = {
        "ok": True,
        "source": "backend",
        "data": {
            "count": len(items),
            "items": items,
        },
    }
    record_tool_call("listar_categorias", "success", 200, perf_counter() - started_at)
    return output
