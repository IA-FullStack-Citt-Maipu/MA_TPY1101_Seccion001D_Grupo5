from datetime import datetime, timedelta, timezone

import jwt
import pytest

from app.auth.jwt_auth import TokenValidationError, extract_role_from_claims, verify_and_decode_jwt
from app.config import settings


def _token(
    *,
    secret: str,
    issuer: str = "panol-backend",
    audience: str | None = None,
    exp_delta: int = 3600,
    iat_delta: int = 0,
    nbf_delta: int = 0,
) -> str:
    now = datetime.now(timezone.utc)
    payload = {
        "iss": issuer,
        "sub": "11111111-1111-1111-1111-111111111111",
        "role": "COORDINADOR",
        "iat": int((now + timedelta(seconds=iat_delta)).timestamp()),
        "nbf": int((now + timedelta(seconds=nbf_delta)).timestamp()),
        "exp": int((now + timedelta(seconds=exp_delta)).timestamp()),
    }
    if audience:
        payload["aud"] = audience
    return jwt.encode(payload, secret, algorithm="HS256")


def _configure(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    monkeypatch.setattr(settings, "JWT_SECRET_KEY", "test-secret-key-with-at-least-32-bytes")
    monkeypatch.setattr(settings, "BOT_SECRET_KEY", "")
    monkeypatch.setattr(settings, "JWT_ISSUER", "panol-backend")
    monkeypatch.setattr(settings, "JWT_AUDIENCE", "")
    monkeypatch.setattr(settings, "JWT_LEEWAY_SECONDS", 1)


def test_verify_and_decode_jwt_success(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    _configure(monkeypatch)
    token = _token(secret="test-secret-key-with-at-least-32-bytes")
    claims = verify_and_decode_jwt(token)
    assert claims["iss"] == "panol-backend"
    assert claims["sub"] == "11111111-1111-1111-1111-111111111111"


def test_verify_and_decode_jwt_invalid_signature(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    _configure(monkeypatch)
    token = _token(secret="wrong-secret-key-with-at-least-32-bytes")
    with pytest.raises(TokenValidationError):
        verify_and_decode_jwt(token)


def test_verify_and_decode_jwt_invalid_issuer(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    _configure(monkeypatch)
    token = _token(secret="test-secret-key-with-at-least-32-bytes", issuer="otro-issuer")
    with pytest.raises(TokenValidationError):
        verify_and_decode_jwt(token)


def test_verify_and_decode_jwt_expired(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    _configure(monkeypatch)
    token = _token(secret="test-secret-key-with-at-least-32-bytes", exp_delta=-60)
    with pytest.raises(TokenValidationError):
        verify_and_decode_jwt(token)


def test_verify_and_decode_jwt_nbf_in_future(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    _configure(monkeypatch)
    token = _token(secret="test-secret-key-with-at-least-32-bytes", nbf_delta=120)
    with pytest.raises(TokenValidationError):
        verify_and_decode_jwt(token)


def test_verify_and_decode_jwt_requires_audience_when_configured(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    _configure(monkeypatch)
    monkeypatch.setattr(settings, "JWT_AUDIENCE", "panol-client")
    token_missing_aud = _token(secret="test-secret-key-with-at-least-32-bytes")
    with pytest.raises(TokenValidationError):
        verify_and_decode_jwt(token_missing_aud)

    token_with_aud = _token(secret="test-secret-key-with-at-least-32-bytes", audience="panol-client")
    claims = verify_and_decode_jwt(token_with_aud)
    assert claims["aud"] == "panol-client"


def test_extract_role_from_claims_priority() -> None:
    claims = {
        "role": "ROLE_DIRECTOR",
        "user_role": "DOCENTE",
        "roles": ["COORDINADOR"],
    }
    assert extract_role_from_claims(claims) == "DIRECTOR"
