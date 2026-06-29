from __future__ import annotations

from time import perf_counter
from typing import Any

from langchain_core.tools import tool

from app.client.backend import backend_client
from app.observability.metrics import record_tool_call
from app.tools.common import build_stat_group_block, build_tool_output, resolve_implement


@tool
def consultar_stock(implemento: str) -> dict[str, Any]:
    """
    Consulta el stock de un implemento usando su nombre o identificador tecnico.
    """
    started_at = perf_counter()
    resolved, error = resolve_implement(implemento)
    if error is not None:
        status_code = int(error.get("status_code", 500))
        record_tool_call("consultar_stock", "error", status_code, perf_counter() - started_at)
        return error

    implement_uuid = str(resolved.get("uuid") or "").strip()
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

    resolved_name = str(resolved.get("name") or resolved.get("nombre") or implemento).strip()
    output = build_tool_output(
        data={
            "implemento": resolved_name,
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
        },
        summary=f"Stock consultado para {resolved_name}.",
        ui_blocks=[
            build_stat_group_block(
                f"Stock de {resolved_name}",
                [
                    {"label": "Disponible", "value": str(counters.get("available") or 0)},
                    {"label": "Reservado", "value": str(counters.get("reserved") or 0)},
                    {"label": "Prestado", "value": str(counters.get("loaned") or 0)},
                    {"label": "Daniado", "value": str(counters.get("damaged") or 0)},
                    {"label": "Total", "value": str(counters.get("total_stock") or 0)},
                ],
            )
        ],
        technical={"implement_uuid": payload.get("implement_uuid"), "nombre": resolved_name},
    )
    record_tool_call("consultar_stock", "success", 200, perf_counter() - started_at)
    return output
