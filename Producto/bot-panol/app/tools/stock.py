from typing import Any
from time import perf_counter

from langchain_core.tools import tool

from app.client.backend import backend_client
from app.observability.metrics import record_tool_call


@tool
def consultar_stock(implement_uuid: str) -> dict[str, Any]:
    """
    Consulta stock por UUID y retorna contadores mas una muestra de hasta 5 individuos.
    """
    started_at = perf_counter()
    result = backend_client.get_safe(f"/api/v2/implements/{implement_uuid}/stock")
    if not result.get("ok"):
        status_code = int(result.get("status_code", 500))
        record_tool_call("consultar_stock", "error", status_code, perf_counter() - started_at)
        return result

    payload = result.get("data", {})
    if not isinstance(payload, dict):
        payload = {}

    counters = payload.get("stock")
    if not isinstance(counters, dict):
        counters = {}

    individuals = payload.get("individuals")
    if not isinstance(individuals, list):
        individuals = []

    preview = []
    for individual in individuals[:5]:
        if not isinstance(individual, dict):
            continue
        preview.append(
            {
                "uuid": individual.get("uuid"),
                "asset_code": individual.get("asset_code"),
                "status": individual.get("status"),
                "condition": individual.get("condition"),
                "notes": individual.get("notes"),
                "current_location_uuid": individual.get("current_location_uuid"),
                "active": individual.get("active"),
            }
        )

    output = {
        "ok": True,
        "source": "backend",
        "data": {
            "implement_uuid": payload.get("implement_uuid"),
            "item_type": payload.get("item_type"),
            "stock": {
                "total_stock": counters.get("total_stock"),
                "min_stock": counters.get("min_stock"),
                "available": counters.get("available"),
                "reserved": counters.get("reserved"),
                "loaned": counters.get("loaned"),
                "damaged": counters.get("damaged"),
            },
            "individuals_count": len(individuals),
            "individuals_preview": preview,
        },
    }
    record_tool_call("consultar_stock", "success", 200, perf_counter() - started_at)
    return output
