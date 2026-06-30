from contextvars import ContextVar


_jwt_token: ContextVar[str] = ContextVar("jwt_token", default="")
_request_id: ContextVar[str] = ContextVar("request_id", default="")
_user_role: ContextVar[str] = ContextVar("user_role", default="")
_user_uuid: ContextVar[str] = ContextVar("user_uuid", default="")
_query_intent: ContextVar[str] = ContextVar("query_intent", default="read_query")


def set_token(token: str) -> None:
    _jwt_token.set(token)


def get_token() -> str:
    return _jwt_token.get()


def set_request_id(request_id: str) -> None:
    _request_id.set(request_id)


def get_request_id() -> str:
    return _request_id.get()


def set_user_role(role: str) -> None:
    _user_role.set(role)


def get_user_role() -> str:
    return _user_role.get()


def set_user_uuid(user_uuid: str) -> None:
    _user_uuid.set(user_uuid)


def get_user_uuid() -> str:
    return _user_uuid.get()


def set_query_intent(intent: str) -> None:
    _query_intent.set(intent)


def get_query_intent() -> str:
    return _query_intent.get()
