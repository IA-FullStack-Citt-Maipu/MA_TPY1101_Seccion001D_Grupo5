from typing import Any
from time import perf_counter

from langchain_core.tools import tool

from app.client.backend import backend_client
from app.observability.metrics import record_tool_call


_VALID_LOAN_STATES = {
    "pending",
    "approved",
    "prepared",
    "rejected",
    "delivered",
    "completed",
    "cancelled",
    "expired",
    "overdue",
}

_VALID_LOAN_STATES_MESSAGE = (
    "estado invalido. Usa pending, approved, prepared, rejected, "
    "delivered, completed, cancelled, expired o overdue."
)


def _safe_int(value: Any) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return 0


def _normalize_estado(value: str | None) -> str | None:
    if value is None:
        return None
    normalized = value.strip().lower()
    if not normalized:
        return None
    return normalized


def _normalize_limited_items(value: int | None) -> int:
    if value is None:
        return 20
    if value < 1:
        return 1
    if value > 100:
        return 100
    return value


def _build_pagination_params(page: int, size: int) -> dict[str, int]:
    return {
        "page": page,
        "size": size,
    }


def _extract_page_payload(payload: Any, requested_page: int) -> tuple[list[dict[str, Any]], bool, int]:
    if isinstance(payload, list):
        items = [row for row in payload if isinstance(row, dict)]
        return items, False, requested_page

    if not isinstance(payload, dict):
        return [], False, requested_page

    raw_items = payload.get("items")
    items = raw_items if isinstance(raw_items, list) else []
    current_page = _safe_int(payload.get("page")) or requested_page
    total_pages = _safe_int(payload.get("total_pages"))
    has_next = payload.get("has_next")

    if isinstance(has_next, bool):
        return [row for row in items if isinstance(row, dict)], has_next, current_page

    if total_pages > 0:
        return [row for row in items if isinstance(row, dict)], current_page < total_pages, current_page

    return [row for row in items if isinstance(row, dict)], False, current_page


def _build_loan_summary(row: dict[str, Any]) -> dict[str, Any]:
    items = row.get("items")
    if not isinstance(items, list):
        items = []

    requested_total = 0
    reserved_total = 0
    delivered_total = 0

    for item in items:
        if not isinstance(item, dict):
            continue
        requested_total += _safe_int(item.get("requested_quantity"))
        reserved_total += _safe_int(item.get("reserved_quantity"))
        delivered_total += _safe_int(item.get("delivered_quantity"))

    return {
        "uuid": row.get("uuid"),
        "status": row.get("status"),
        "scheduled_at": row.get("scheduled_at"),
        "created_at": row.get("created_at"),
        "requester_uuid": row.get("requester_uuid"),
        "room": row.get("room"),
        "subject": row.get("subject"),
        "items_count": len(items),
        "totals": {
            "requested": requested_total,
            "reserved": reserved_total,
            "delivered": delivered_total,
        },
    }


@tool
def listar_prestamos(
    estado: str | None = None,
    requester_uuid: str | None = None,
    limite: int | None = 20,
) -> dict[str, Any]:
    """
    Lista prestamos y permite filtrar por estado y solicitante.
    """
    started_at = perf_counter()
    normalized_estado = _normalize_estado(estado)
    normalized_limite = _normalize_limited_items(limite)
    normalized_requester = (requester_uuid or "").strip() or None

    if normalized_estado and normalized_estado not in _VALID_LOAN_STATES:
        result = {
            "ok": False,
            "source": "backend",
            "status_code": 400,
            "error_code": "LOAN_STATUS_INVALID",
            "message": _VALID_LOAN_STATES_MESSAGE,
            "timestamp": None,
        }
        record_tool_call("listar_prestamos", "error", 400, perf_counter() - started_at)
        return result

    filtered: list[dict[str, Any]] = []
    page = 1
    page_size = min(normalized_limite, 100)
    pages_scanned = 0

    while True:
        result = backend_client.get_safe(
            "/api/v2/loans",
            params=_build_pagination_params(page=page, size=page_size),
        )
        if not result.get("ok"):
            status_code = int(result.get("status_code", 500))
            record_tool_call("listar_prestamos", "error", status_code, perf_counter() - started_at)
            return result

        payload = result.get("data", {})
        items, has_next, current_page = _extract_page_payload(payload, requested_page=page)
        pages_scanned += 1

        for row in items:
            row_status = str(row.get("status") or "").strip().lower()
            row_requester = str(row.get("requester_uuid") or "").strip().lower()

            if normalized_estado and row_status != normalized_estado:
                continue
            if normalized_requester and row_requester != normalized_requester.lower():
                continue

            filtered.append(_build_loan_summary(row))
            if len(filtered) >= normalized_limite:
                break

        if len(filtered) >= normalized_limite:
            break
        if not has_next:
            break

        page = current_page + 1

    sliced = filtered[:normalized_limite]
    output = {
        "ok": True,
        "source": "backend",
        "data": {
            "filters": {
                "estado": normalized_estado,
                "requester_uuid": normalized_requester,
                "limite": normalized_limite,
            },
            "count": len(sliced),
            "items": sliced,
            "pages_scanned": pages_scanned,
        },
    }
    record_tool_call("listar_prestamos", "success", 200, perf_counter() - started_at)
    return output
