from typing import Any
from time import perf_counter

from langchain_core.tools import tool

from app.client.backend import backend_client
from app.observability.metrics import record_tool_call


@tool
def buscar_implementos(nombre: str) -> dict[str, Any]:
    """
    Busca implementos por nombre y devuelve un resumen de hasta 10 resultados.
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
    for item in payload[:10]:
        if not isinstance(item, dict):
            continue

        stock = item.get("stock")
        if not isinstance(stock, dict):
            stock = {}

        items.append(
            {
                "uuid": item.get("uuid"),
                "nombre": item.get("name"),
                "activo": item.get("active"),
                "disponible": item.get("available"),
                "stock": {
                    "total_stock": stock.get("total_stock"),
                    "min_stock": stock.get("min_stock"),
                    "available": stock.get("available"),
                    "reserved": stock.get("reserved"),
                    "loaned": stock.get("loaned"),
                    "damaged": stock.get("damaged"),
                },
            }
        )

    output = {
        "ok": True,
        "source": "backend",
        "data": {
            "query": nombre,
            "count": len(items),
            "items": items,
        },
    }
    record_tool_call("buscar_implementos", "success", 200, perf_counter() - started_at)
    return output
