from __future__ import annotations

import re
import unicodedata
from typing import Any

from app.client.backend import backend_client
from app.client.context import get_query_intent, get_user_role


UUID_PATTERN = re.compile(
    r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$"
)


def safe_int(value: Any) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return 0


def normalize_text(value: str) -> str:
    collapsed = " ".join((value or "").split()).strip().lower()
    normalized = unicodedata.normalize("NFKD", collapsed)
    return "".join(char for char in normalized if not unicodedata.combining(char))


def is_uuid_like(value: str | None) -> bool:
    if not value:
        return False
    return bool(UUID_PATTERN.match(value.strip()))


def identifiers_allowed() -> bool:
    return get_user_role() == "COORDINADOR" and get_query_intent() == "identifier_request"


def build_entity_list_block(title: str, entities: list[dict[str, Any]]) -> dict[str, Any]:
    return {
        "type": "entity_list",
        "title": title,
        "entities": entities,
    }


def build_stat_group_block(title: str, stats: list[dict[str, str]]) -> dict[str, Any]:
    return {
        "type": "stat_group",
        "title": title,
        "stats": stats,
    }


def build_tool_output(
    *,
    data: dict[str, Any],
    summary: str,
    ui_blocks: list[dict[str, Any]],
    technical: dict[str, Any] | None = None,
) -> dict[str, Any]:
    payload = {
        "ok": True,
        "source": "backend",
        "data": data,
        "presentation": {
            "summary": summary,
            "ui_blocks": ui_blocks,
        },
    }
    if technical and identifiers_allowed():
        payload["presentation"]["technical"] = technical
    return payload


def maybe_identifier(identifier: str | None) -> str | None:
    if not identifiers_allowed():
        return None
    normalized = (identifier or "").strip()
    return normalized or None


def choose_best_implement_match(query: str, items: list[dict[str, Any]]) -> dict[str, Any] | None:
    if not items:
        return None

    normalized_query = normalize_text(query)
    exact_matches = [
        item for item in items
        if normalize_text(str(item.get("name") or "")) == normalized_query
    ]
    if exact_matches:
        return exact_matches[0]

    prefix_matches = [
        item for item in items
        if normalize_text(str(item.get("name") or "")).startswith(normalized_query)
    ]
    if prefix_matches:
        return prefix_matches[0]

    contains_matches = [
        item for item in items
        if normalized_query in normalize_text(str(item.get("name") or ""))
    ]
    if contains_matches:
        return contains_matches[0]

    return items[0]


def resolve_implement(query: str) -> tuple[dict[str, Any] | None, dict[str, Any] | None]:
    normalized_query = (query or "").strip()
    if not normalized_query:
        return None, {
            "ok": False,
            "source": "backend",
            "status_code": 400,
            "error_code": "IMPLEMENT_QUERY_REQUIRED",
            "message": "Debes indicar el nombre del implemento.",
            "timestamp": None,
        }

    if is_uuid_like(normalized_query):
        result = backend_client.get_safe(f"/api/v2/implements/{normalized_query}")
        if not result.get("ok"):
            return None, result
        payload = result.get("data", {})
        if not isinstance(payload, dict):
            payload = {}
        return payload, None

    result = backend_client.get_safe("/api/v2/implements", params={"name": normalized_query})
    if not result.get("ok"):
        return None, result

    payload = result.get("data", [])
    if not isinstance(payload, list):
        payload = []

    items = [row for row in payload if isinstance(row, dict)]
    match = choose_best_implement_match(normalized_query, items)
    if match is None:
        return None, {
            "ok": False,
            "source": "backend",
            "status_code": 404,
            "error_code": "IMPLEMENT_NOT_FOUND",
            "message": "No encontre un implemento que coincida con la consulta indicada.",
            "timestamp": None,
        }

    return match, None
