from __future__ import annotations

import re
import unicodedata
from typing import Any


_UUID_PATTERN = re.compile(
    r"\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}\b"
)
_PARAGRAPH_SPLIT_PATTERN = re.compile(r"\n\s*\n+")
_LIST_OR_HEADING_PATTERN = re.compile(r"^\s*(?:#{1,6}\s+|[-*+•◦]\s+|\d+[.)]\s+)")
_SENSITIVE_LABEL_PATTERNS = [
    re.compile(pattern, re.IGNORECASE)
    for pattern in (
        r"\brequester_uuid\b",
        r"\bperformed_by\b",
        r"\bindividual_uuid\b",
        r"\basset_code\b",
        r"\bnotes?\b",
    )
]


def _normalize_text(value: str) -> str:
    normalized = unicodedata.normalize("NFKD", value or "")
    without_accents = "".join(char for char in normalized if not unicodedata.combining(char))
    collapsed = re.sub(r"[\s`*_>#-]+", " ", without_accents)
    return collapsed.strip().lower()


def _extract_structured_phrases(ui_blocks: list[dict[str, Any]]) -> set[str]:
    phrases: set[str] = set()
    for block in ui_blocks:
        if not isinstance(block, dict):
            continue

        title = block.get("title")
        if isinstance(title, str) and title.strip():
            phrases.add(_normalize_text(title))

        if block.get("type") == "entity_list":
            entities = block.get("entities")
            if not isinstance(entities, list):
                continue
            for entity in entities:
                if not isinstance(entity, dict):
                    continue
                for key in ("title", "subtitle"):
                    value = entity.get(key)
                    if isinstance(value, str) and value.strip():
                        phrases.add(_normalize_text(value))
                for key in ("meta", "badges"):
                    items = entity.get(key)
                    if not isinstance(items, list):
                        continue
                    for item in items:
                        if isinstance(item, str) and item.strip():
                            phrases.add(_normalize_text(item))
            continue

        if block.get("type") == "stat_group":
            stats = block.get("stats")
            if not isinstance(stats, list):
                continue
            for stat in stats:
                if not isinstance(stat, dict):
                    continue
                label = stat.get("label")
                value = stat.get("value")
                if isinstance(label, str) and label.strip():
                    phrases.add(_normalize_text(label))
                if isinstance(label, str) and label.strip() and isinstance(value, str) and value.strip():
                    phrases.add(_normalize_text(f"{label}: {value}"))

    phrases.discard("")
    return phrases


def _is_structured_duplicate_paragraph(paragraph: str, structured_phrases: set[str]) -> bool:
    lines = [line.strip() for line in paragraph.splitlines() if line.strip()]
    if not lines:
        return False

    normalized_lines = [_normalize_text(line) for line in lines]
    if any(normalized_line in structured_phrases for normalized_line in normalized_lines):
        return True

    if len(lines) > 1 and any(_LIST_OR_HEADING_PATTERN.match(line) for line in lines):
        return True

    return False


def _sanitize_structured_response_text(text: str, ui_blocks: list[dict[str, Any]]) -> str:
    paragraphs = [paragraph.strip() for paragraph in _PARAGRAPH_SPLIT_PATTERN.split(text) if paragraph.strip()]
    if not paragraphs:
        return ""

    structured_phrases = _extract_structured_phrases(ui_blocks)
    kept_paragraphs: list[str] = []
    for paragraph in paragraphs:
        if _is_structured_duplicate_paragraph(paragraph, structured_phrases):
            continue
        kept_paragraphs.append(paragraph)
        break

    return "\n\n".join(kept_paragraphs).strip()


def sanitize_response_text(
    text: str,
    *,
    allow_identifiers: bool,
    ui_blocks: list[dict[str, Any]] | None = None,
) -> str:
    sanitized = (text or "").strip()
    if not sanitized:
        return ""

    for pattern in _SENSITIVE_LABEL_PATTERNS:
        sanitized = pattern.sub("[dato sensible oculto]", sanitized)

    if not allow_identifiers:
        sanitized = _UUID_PATTERN.sub("[identificador oculto]", sanitized)

    if ui_blocks:
        sanitized = _sanitize_structured_response_text(sanitized, ui_blocks)

    return sanitized.strip()
