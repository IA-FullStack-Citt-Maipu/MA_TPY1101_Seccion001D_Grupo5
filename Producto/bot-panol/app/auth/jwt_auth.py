from typing import Any

import jwt
from jwt.exceptions import InvalidTokenError

from app.config import settings


class TokenValidationError(RuntimeError):
    pass


def extract_bearer_token(authorization: str | None) -> str | None:
    if not authorization:
        return None

    parts = authorization.strip().split()
    if len(parts) != 2:
        return None
    if parts[0].lower() != "bearer":
        return None
    token = parts[1].strip()
    return token if token else None


def _resolve_jwt_secret() -> str:
    return (settings.JWT_SECRET_KEY or settings.BOT_SECRET_KEY).strip()


def verify_and_decode_jwt(token: str) -> dict[str, Any]:
    secret = _resolve_jwt_secret()
    if not secret:
        raise TokenValidationError("JWT secret is missing.")

    options = {
        "verify_signature": True,
        "verify_exp": True,
        "verify_nbf": True,
        "verify_iat": True,
        "verify_iss": True,
        "require": ["exp", "iss", "sub"],
    }

    audience = settings.JWT_AUDIENCE.strip()
    if not audience:
        options["verify_aud"] = False

    try:
        claims = jwt.decode(
            token,
            secret,
            algorithms=["HS256"],
            issuer=settings.JWT_ISSUER,
            audience=audience or None,
            leeway=settings.JWT_LEEWAY_SECONDS,
            options=options,
        )
    except InvalidTokenError as exc:
        raise TokenValidationError("Invalid JWT token.") from exc

    if not isinstance(claims, dict):
        raise TokenValidationError("Invalid JWT claims payload.")
    return claims


def _normalize_role(raw: Any) -> str | None:
    if not isinstance(raw, str):
        return None

    normalized = raw.strip().upper()
    if normalized.startswith("ROLE_"):
        normalized = normalized[5:]

    if normalized in {"DOCENTE", "COORDINADOR", "DIRECTOR"}:
        return normalized
    if "COORD" in normalized:
        return "COORDINADOR"
    if "DIRECT" in normalized:
        return "DIRECTOR"
    if "DOCENTE" in normalized:
        return "DOCENTE"
    return None


def extract_role_from_claims(claims: dict[str, Any]) -> str:
    role = _normalize_role(claims.get("role"))
    if role:
        return role

    role = _normalize_role(claims.get("user_role"))
    if role:
        return role

    roles_claim = claims.get("roles")
    if isinstance(roles_claim, str):
        role = _normalize_role(roles_claim)
        if role:
            return role
    elif isinstance(roles_claim, list):
        for candidate in roles_claim:
            role = _normalize_role(candidate)
            if role:
                return role

    return "DOCENTE"
