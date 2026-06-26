from time import perf_counter
from typing import Any

from langchain_core.tools import tool

from app.client.backend import backend_client
from app.observability.metrics import record_tool_call


@tool
def detalle_implemento(implement_uuid: str) -> dict[str, Any]:
    """
    Obtiene el detalle de un implemento por UUID.
    """
    started_at = perf_counter()
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

    output = {
        "ok": True,
        "source": "backend",
        "data": {
            "uuid": payload.get("uuid"),
            "nombre": payload.get("name"),
            "descripcion": payload.get("description"),
            "item_type": payload.get("item_type"),
            "activo": payload.get("active"),
            "display_location": payload.get("display_location"),
            "categoria": {
                "uuid": category.get("uuid"),
                "nombre": category.get("name"),
                "activa": category.get("active"),
            }
            if category
            else None,
            "ubicacion": {
                "uuid": location.get("uuid"),
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
    }
    record_tool_call("detalle_implemento", "success", 200, perf_counter() - started_at)
    return output
