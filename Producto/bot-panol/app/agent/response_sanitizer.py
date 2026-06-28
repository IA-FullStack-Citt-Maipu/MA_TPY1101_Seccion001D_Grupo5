from __future__ import annotations

import re


_UUID_PATTERN = re.compile(
    r"\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}\b"
)
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


def sanitize_response_text(text: str, *, allow_identifiers: bool) -> str:
    sanitized = (text or "").strip()
    if not sanitized:
        return ""

    for pattern in _SENSITIVE_LABEL_PATTERNS:
        sanitized = pattern.sub("[dato sensible oculto]", sanitized)

    if not allow_identifiers:
        sanitized = _UUID_PATTERN.sub("[identificador oculto]", sanitized)

    return sanitized.strip()
