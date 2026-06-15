from contextvars import ContextVar


_jwt_token: ContextVar[str] = ContextVar("jwt_token", default="")
_request_id: ContextVar[str] = ContextVar("request_id", default="")


def set_token(token: str) -> None:
    _jwt_token.set(token)


def get_token() -> str:
    return _jwt_token.get()


def set_request_id(request_id: str) -> None:
    _request_id.set(request_id)


def get_request_id() -> str:
    return _request_id.get()
