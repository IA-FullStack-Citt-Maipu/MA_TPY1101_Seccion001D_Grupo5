from datetime import date, datetime
from time import perf_counter
from typing import Any, Callable

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
_VALID_LOAN_DATE_MESSAGE = "fecha invalida. Usa formato YYYY-MM-DD."


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


def _normalize_fecha(value: str | None) -> tuple[date | None, str | None]:
    if value is None or not value.strip():
        today = datetime.now().astimezone().date()
        return today, today.isoformat()

    normalized = value.strip()
    try:
        parsed = date.fromisoformat(normalized)
    except ValueError:
        return None, None
    return parsed, parsed.isoformat()


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


def _extract_scheduled_date(row: dict[str, Any]) -> date | None:
    raw_value = row.get("scheduled_at")
    if not isinstance(raw_value, str) or not raw_value.strip():
        return None

    normalized = raw_value.strip().replace("Z", "+00:00")
    try:
        return datetime.fromisoformat(normalized).astimezone().date()
    except ValueError:
        return None


def _iter_filtered_loans(
    normalized_limite: int,
    predicate: Callable[[dict[str, Any]], bool],
    tool_name: str,
    started_at: float,
) -> tuple[dict[str, Any] | None, list[dict[str, Any]], int]:
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
            record_tool_call(tool_name, "error", status_code, perf_counter() - started_at)
            return result, [], pages_scanned

        payload = result.get("data", {})
        items, has_next, current_page = _extract_page_payload(payload, requested_page=page)
        pages_scanned += 1

        for row in items:
            if not predicate(row):
                continue

            filtered.append(_build_loan_summary(row))
            if len(filtered) >= normalized_limite:
                break

        if len(filtered) >= normalized_limite:
            break
        if not has_next:
            break

        page = current_page + 1

    return None, filtered[:normalized_limite], pages_scanned


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

    def predicate(row: dict[str, Any]) -> bool:
        row_status = str(row.get("status") or "").strip().lower()
        row_requester = str(row.get("requester_uuid") or "").strip().lower()

        if normalized_estado and row_status != normalized_estado:
            return False
        if normalized_requester and row_requester != normalized_requester.lower():
            return False
        return True

    error_result, sliced, pages_scanned = _iter_filtered_loans(
        normalized_limite=normalized_limite,
        predicate=predicate,
        tool_name="listar_prestamos",
        started_at=started_at,
    )
    if error_result is not None:
        return error_result

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


@tool
def listar_prestamos_programados(
    fecha: str | None = None,
    limite: int | None = 20,
) -> dict[str, Any]:
    """
    Lista prestamos programados para una fecha especifica en formato YYYY-MM-DD.
    Si no se indica fecha, usa la fecha local actual del sistema.
    """
    started_at = perf_counter()
    normalized_limite = _normalize_limited_items(limite)
    target_date, normalized_fecha = _normalize_fecha(fecha)

    if target_date is None or normalized_fecha is None:
        result = {
            "ok": False,
            "source": "backend",
            "status_code": 400,
            "error_code": "LOAN_DATE_INVALID",
            "message": _VALID_LOAN_DATE_MESSAGE,
            "timestamp": None,
        }
        record_tool_call("listar_prestamos_programados", "error", 400, perf_counter() - started_at)
        return result

    def predicate(row: dict[str, Any]) -> bool:
        scheduled_date = _extract_scheduled_date(row)
        return scheduled_date == target_date

    error_result, sliced, pages_scanned = _iter_filtered_loans(
        normalized_limite=normalized_limite,
        predicate=predicate,
        tool_name="listar_prestamos_programados",
        started_at=started_at,
    )
    if error_result is not None:
        return error_result

    output = {
        "ok": True,
        "source": "backend",
        "data": {
            "filters": {
                "fecha": normalized_fecha,
                "limite": normalized_limite,
            },
            "count": len(sliced),
            "items": sliced,
            "pages_scanned": pages_scanned,
        },
    }
    record_tool_call("listar_prestamos_programados", "success", 200, perf_counter() - started_at)
    return output
