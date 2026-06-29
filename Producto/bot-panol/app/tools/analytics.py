from __future__ import annotations

from datetime import datetime, timedelta
from time import perf_counter
from typing import Any

from langchain_core.tools import tool

from app.client.backend import backend_client
from app.observability.metrics import record_tool_call
from app.tools.common import (
    build_entity_list_block,
    build_stat_group_block,
    build_tool_output,
    resolve_implement,
    safe_int,
)


def _normalize_limited_items(value: int | None, *, default: int, maximum: int) -> int:
    if value is None:
        return default
    if value < 1:
        return 1
    if value > maximum:
        return maximum
    return value


def _build_pagination_params(page: int, size: int) -> dict[str, int]:
    return {"page": page, "size": size}


def _extract_page_payload(payload: Any, requested_page: int) -> tuple[list[dict[str, Any]], bool, int]:
    if isinstance(payload, list):
        items = [row for row in payload if isinstance(row, dict)]
        return items, False, requested_page

    if not isinstance(payload, dict):
        return [], False, requested_page

    raw_items = payload.get("items")
    items = raw_items if isinstance(raw_items, list) else []
    current_page = safe_int(payload.get("page")) or requested_page
    total_pages = safe_int(payload.get("total_pages"))
    has_next = payload.get("has_next")

    if isinstance(has_next, bool):
        return [row for row in items if isinstance(row, dict)], has_next, current_page

    if total_pages > 0:
        return [row for row in items if isinstance(row, dict)], current_page < total_pages, current_page

    return [row for row in items if isinstance(row, dict)], False, current_page


def _iter_all_loans(*, tool_name: str, started_at: float) -> tuple[dict[str, Any] | None, list[dict[str, Any]], int]:
    page = 1
    page_size = 100
    pages_scanned = 0
    collected: list[dict[str, Any]] = []

    while True:
        result = backend_client.get_safe("/api/v2/loans", params=_build_pagination_params(page=page, size=page_size))
        if not result.get("ok"):
            status_code = int(result.get("status_code", 500))
            record_tool_call(tool_name, "error", status_code, perf_counter() - started_at)
            return result, [], pages_scanned

        payload = result.get("data", {})
        items, has_next, current_page = _extract_page_payload(payload, requested_page=page)
        pages_scanned += 1
        collected.extend(items)

        if not has_next:
            break
        page = current_page + 1

    return None, collected, pages_scanned


def _parse_scheduled_at(raw_value: Any) -> datetime | None:
    if not isinstance(raw_value, str) or not raw_value.strip():
        return None

    normalized = raw_value.strip().replace("Z", "+00:00")
    try:
        return datetime.fromisoformat(normalized)
    except ValueError:
        return None


@tool
def contar_prestamos_por_producto(implemento: str) -> dict[str, Any]:
    """
    Cuenta cuantos prestamos historicos distintos incluyen un implemento especifico.
    """
    started_at = perf_counter()
    resolved, error = resolve_implement(implemento)
    if error is not None:
        status_code = int(error.get("status_code", 500))
        record_tool_call("contar_prestamos_por_producto", "error", status_code, perf_counter() - started_at)
        return error

    target_uuid = str(resolved.get("uuid") or "").strip()
    target_name = str(resolved.get("name") or implemento).strip()
    error_result, rows, pages_scanned = _iter_all_loans(
        tool_name="contar_prestamos_por_producto",
        started_at=started_at,
    )
    if error_result is not None:
        return error_result

    unique_loans: set[str] = set()
    status_breakdown: dict[str, int] = {}

    for row in rows:
        loan_uuid = str(row.get("uuid") or "").strip()
        items = row.get("items")
        if not loan_uuid or not isinstance(items, list):
            continue

        matched = False
        for item in items:
            if not isinstance(item, dict):
                continue
            row_implement_uuid = str(item.get("implement_uuid") or "").strip()
            if row_implement_uuid != target_uuid:
                continue
            matched = True
            break

        if not matched or loan_uuid in unique_loans:
            continue

        unique_loans.add(loan_uuid)
        status = str(row.get("status") or "").strip().lower() or "unknown"
        status_breakdown[status] = status_breakdown.get(status, 0) + 1

    output = build_tool_output(
        data={
            "implement_name": target_name,
            "loan_count": len(unique_loans),
            "status_breakdown": status_breakdown,
            "pages_scanned": pages_scanned,
            "count_mode": "unique_loans",
        },
        summary=f"{target_name} aparece en {len(unique_loans)} prestamos historicos distintos.",
        ui_blocks=[
            build_stat_group_block(
                f"Prestamos historicos de {target_name}",
                [{"label": status, "value": str(count)} for status, count in sorted(status_breakdown.items())]
                or [{"label": "Prestamos", "value": "0"}],
            )
        ],
        technical={"implement_uuid": target_uuid, "nombre": target_name},
    )
    record_tool_call("contar_prestamos_por_producto", "success", 200, perf_counter() - started_at)
    return output


@tool
def recomendar_reposicion(limite: int | None = 5) -> dict[str, Any]:
    """
    Sugiere implementos para reposicion usando una regla operativa simple basada en stock minimo y rotacion reciente.
    """
    started_at = perf_counter()
    normalized_limit = _normalize_limited_items(limite, default=5, maximum=10)
    implements_result = backend_client.get_safe("/api/v2/implements")
    if not implements_result.get("ok"):
        status_code = int(implements_result.get("status_code", 500))
        record_tool_call("recomendar_reposicion", "error", status_code, perf_counter() - started_at)
        return implements_result

    error_result, loan_rows, pages_scanned = _iter_all_loans(
        tool_name="recomendar_reposicion",
        started_at=started_at,
    )
    if error_result is not None:
        return error_result

    implement_rows = implements_result.get("data", [])
    if not isinstance(implement_rows, list):
        implement_rows = []

    recent_threshold = datetime.now().astimezone() - timedelta(days=90)
    recent_loan_counts: dict[str, int] = {}
    for loan_row in loan_rows:
        scheduled_at = _parse_scheduled_at(loan_row.get("scheduled_at"))
        if scheduled_at is None or scheduled_at.astimezone() < recent_threshold:
            continue
        items = loan_row.get("items")
        if not isinstance(items, list):
            continue
        seen_in_loan: set[str] = set()
        for item in items:
            if not isinstance(item, dict):
                continue
            implement_uuid = str(item.get("implement_uuid") or "").strip()
            if not implement_uuid or implement_uuid in seen_in_loan:
                continue
            seen_in_loan.add(implement_uuid)
            recent_loan_counts[implement_uuid] = recent_loan_counts.get(implement_uuid, 0) + 1

    candidates: list[dict[str, Any]] = []
    technical_items: list[dict[str, Any]] = []
    for row in implement_rows:
        if not isinstance(row, dict):
            continue

        stock = row.get("stock")
        if not isinstance(stock, dict):
            stock = {}
        category = row.get("category")
        if not isinstance(category, dict):
            category = {}

        implement_uuid = str(row.get("uuid") or "").strip()
        available = safe_int(stock.get("available"))
        min_stock = safe_int(stock.get("min_stock"))
        recent_loans = recent_loan_counts.get(implement_uuid, 0)
        below_minimum = min_stock > 0 and available < min_stock
        high_rotation = recent_loans >= 3
        needs_attention = below_minimum or (high_rotation and available <= min_stock + 2)
        if not needs_attention:
            continue

        stock_gap = max(min_stock - available, 0)
        score = (100 if below_minimum else 50) + (stock_gap * 10) + recent_loans
        reasons: list[str] = []
        if below_minimum:
            reasons.append("stock disponible bajo el minimo")
        if high_rotation:
            reasons.append("alta rotacion reciente en prestamos")

        candidates.append(
            {
                "nombre": row.get("name"),
                "categoria": category.get("name"),
                "stock": {
                    "total_stock": stock.get("total_stock"),
                    "min_stock": stock.get("min_stock"),
                    "available": stock.get("available"),
                    "reserved": stock.get("reserved"),
                    "loaned": stock.get("loaned"),
                    "damaged": stock.get("damaged"),
                },
                "recent_loan_count": recent_loans,
                "stock_gap": stock_gap,
                "recommendation_level": "critical" if below_minimum and high_rotation else ("high" if below_minimum else "monitor"),
                "reasons": reasons,
                "priority_score": score,
            }
        )
        technical_items.append({"nombre": row.get("name"), "uuid": row.get("uuid")})

    candidates.sort(
        key=lambda item: (
            -safe_int(item.get("priority_score")),
            -safe_int(item.get("stock_gap")),
            -safe_int(item.get("recent_loan_count")),
            str(item.get("nombre") or ""),
        )
    )

    sliced = candidates[:normalized_limit]
    output = build_tool_output(
        data={
            "count": len(sliced),
            "pages_scanned": pages_scanned,
            "recent_window_days": 90,
            "items": sliced,
        },
        summary=f"Se priorizaron {len(sliced)} implementos para reposicion.",
        ui_blocks=[
            build_entity_list_block(
                "Reposicion sugerida",
                [
                    {
                        "title": item.get("nombre"),
                        "subtitle": item.get("categoria") or "Sin categoria",
                        "meta": [
                            f"Disponible: {item.get('stock', {}).get('available') or 0}",
                            f"Minimo: {item.get('stock', {}).get('min_stock') or 0}",
                            f"Rotacion reciente: {item.get('recent_loan_count') or 0}",
                        ],
                        "badges": [str(item.get("recommendation_level") or "monitor").upper()],
                    }
                    for item in sliced
                ],
            )
        ] if sliced else [],
        technical={"items": technical_items[:normalized_limit]},
    )
    record_tool_call("recomendar_reposicion", "success", 200, perf_counter() - started_at)
    return output


@tool
def resumen_inventario_por_categoria(limite: int | None = 10) -> dict[str, Any]:
    """
    Resume el inventario agrupado por categoria con totales de stock y alertas de stock minimo.
    """
    started_at = perf_counter()
    normalized_limit = _normalize_limited_items(limite, default=10, maximum=25)
    result = backend_client.get_safe("/api/v2/implements")
    if not result.get("ok"):
        status_code = int(result.get("status_code", 500))
        record_tool_call("resumen_inventario_por_categoria", "error", status_code, perf_counter() - started_at)
        return result

    payload = result.get("data", [])
    if not isinstance(payload, list):
        payload = []

    grouped: dict[str, dict[str, Any]] = {}
    technical_items: list[dict[str, Any]] = []
    for row in payload:
        if not isinstance(row, dict):
            continue

        category = row.get("category")
        if not isinstance(category, dict):
            category = {}
        stock = row.get("stock")
        if not isinstance(stock, dict):
            stock = {}

        category_key = str(category.get("name") or "Sin categoria")
        bucket = grouped.setdefault(
            category_key,
            {
                "category_name": category_key,
                "implements_total": 0,
                "low_stock_items": 0,
                "stock": {"total_stock": 0, "available": 0, "reserved": 0, "loaned": 0, "damaged": 0},
            },
        )
        bucket["implements_total"] = safe_int(bucket.get("implements_total")) + 1
        bucket_stock = bucket["stock"]
        bucket_stock["total_stock"] = safe_int(bucket_stock.get("total_stock")) + safe_int(stock.get("total_stock"))
        bucket_stock["available"] = safe_int(bucket_stock.get("available")) + safe_int(stock.get("available"))
        bucket_stock["reserved"] = safe_int(bucket_stock.get("reserved")) + safe_int(stock.get("reserved"))
        bucket_stock["loaned"] = safe_int(bucket_stock.get("loaned")) + safe_int(stock.get("loaned"))
        bucket_stock["damaged"] = safe_int(bucket_stock.get("damaged")) + safe_int(stock.get("damaged"))

        min_stock = safe_int(stock.get("min_stock"))
        available = safe_int(stock.get("available"))
        if min_stock > 0 and available < min_stock:
            bucket["low_stock_items"] = safe_int(bucket.get("low_stock_items")) + 1

        technical_items.append({"implemento": row.get("name"), "categoria_uuid": category.get("uuid")})

    categories = list(grouped.values())
    categories.sort(
        key=lambda item: (
            -safe_int(item.get("low_stock_items")),
            -safe_int(item.get("implements_total")),
            str(item.get("category_name") or ""),
        )
    )

    sliced = categories[:normalized_limit]
    output = build_tool_output(
        data={"count": len(sliced), "categories": sliced},
        summary=f"Se resumieron {len(sliced)} categorias de inventario.",
        ui_blocks=[
            build_entity_list_block(
                "Inventario por categoria",
                [
                    {
                        "title": category.get("category_name"),
                        "subtitle": None,
                        "meta": [
                            f"Implementos: {category.get('implements_total') or 0}",
                            f"Stock disponible: {category.get('stock', {}).get('available') or 0}",
                            f"Alertas: {category.get('low_stock_items') or 0}",
                        ],
                        "badges": [],
                    }
                    for category in sliced
                ],
            )
        ] if sliced else [],
        technical={"items": technical_items[:normalized_limit]},
    )
    record_tool_call("resumen_inventario_por_categoria", "success", 200, perf_counter() - started_at)
    return output
