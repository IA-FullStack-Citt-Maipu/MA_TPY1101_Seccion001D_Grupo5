from __future__ import annotations

from datetime import datetime, timezone
import json
import logging
from typing import Any


def configure_logging(level: str = "INFO") -> None:
    logging.basicConfig(
        level=getattr(logging, level.upper(), logging.INFO),
        format="%(message)s",
    )


def log_event(event: str, *, level: str = "INFO", **fields: Any) -> None:
    logger = logging.getLogger("bot_panol")
    payload = {
        "event": event,
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "level": level.upper(),
    }
    for key, value in fields.items():
        if value is not None:
            payload[key] = value

    message = json.dumps(payload, ensure_ascii=False, default=str)
    log_method = getattr(logger, level.lower(), logger.info)
    log_method(message)
