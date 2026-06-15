from time import perf_counter
from typing import Any

from langchain_core.tools import tool

from app.client.backend import backend_client
from app.observability.metrics import record_tool_call


def _safe_int(value: Any) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return 0


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
    for item in payload:
        if not isinstance(item, dict):
            continue

        stock = item.get("stock")
        if not isinstance(stock, dict):
            stock = {}

        active = bool(item.get("active"))
        min_stock = _safe_int(stock.get("min_stock"))
        available = _safe_int(stock.get("available"))

        if not active:
            continue
        if min_stock <= 0:
            continue
        if available >= min_stock:
            continue

        items.append(
            {
                "uuid": item.get("uuid"),
                "nombre": item.get("name"),
                "activo": active,
                "disponible": item.get("available"),
                "stock": {
                    "total_stock": stock.get("total_stock"),
                    "min_stock": stock.get("min_stock"),
                    "available": stock.get("available"),
                    "reserved": stock.get("reserved"),
                    "loaned": stock.get("loaned"),
                    "damaged": stock.get("damaged"),
                },
                "stock_gap": min_stock - available,
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
    record_tool_call("listar_implementos_bajo_stock_minimo", "success", 200, perf_counter() - started_at)
    return output
