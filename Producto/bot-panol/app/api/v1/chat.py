from typing import Literal
from uuid import uuid4

from fastapi import APIRouter, Header
from fastapi.responses import JSONResponse
from langchain_core.messages import AIMessage, BaseMessage, HumanMessage, SystemMessage, ToolMessage
from pydantic import BaseModel, Field

from app.agent.graph import get_graph
from app.agent.nodes import LLMServiceUnavailableError, extract_text_content
from app.agent.prompts import build_system_prompt
from app.auth.jwt_auth import (
    TokenValidationError,
    extract_bearer_token,
    extract_role_from_claims,
    verify_and_decode_jwt,
)
from app.client.context import get_request_id, set_token
from app.config import settings
from app.observability.logger import log_event


router = APIRouter(tags=["chat"])


class HistoryMessage(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1)


class ChatRequest(BaseModel):
    message: str = Field(min_length=1)
    conversation_id: str | None = None
    history: list[HistoryMessage] = Field(default_factory=list)


class ChatResponse(BaseModel):
    response: str
    conversation_id: str
    tools_used: list[str]


def _unauthorized(detail: str) -> JSONResponse:
    return JSONResponse(
        status_code=401,
        content={
            "detail": detail,
            "message": "Se requiere un token de autorizacion valido.",
        },
    )


def _forbidden(detail: str) -> JSONResponse:
    return JSONResponse(
        status_code=403,
        content={
            "detail": detail,
            "message": "No tienes permisos para usar este servicio.",
        },
    )


def _build_messages(role: str, payload: ChatRequest) -> list[BaseMessage]:
    trimmed_history = payload.history[-settings.MAX_HISTORY_MESSAGES :]

    messages: list[BaseMessage] = [
        SystemMessage(content=build_system_prompt(role)),
    ]
    for item in trimmed_history:
        if item.role == "user":
            messages.append(HumanMessage(content=item.content))
        else:
            messages.append(AIMessage(content=item.content))
    messages.append(HumanMessage(content=payload.message))
    return messages


def _extract_executed_tools(messages: list[BaseMessage]) -> list[str]:
    tools_used: list[str] = []
    for message in messages:
        if isinstance(message, ToolMessage) and isinstance(message.name, str) and message.name:
            tools_used.append(message.name)
    return tools_used


@router.post("/chat", response_model=ChatResponse)
def chat(payload: ChatRequest, authorization: str | None = Header(default=None)) -> ChatResponse | JSONResponse:
    request_id = get_request_id()
    token = extract_bearer_token(authorization)
    if not token:
        log_event("chat_unauthorized", request_id=request_id, detail="TOKEN_MISSING")
        return _unauthorized("TOKEN_MISSING")

    try:
        claims = verify_and_decode_jwt(token)
    except TokenValidationError:
        log_event("chat_unauthorized", request_id=request_id, detail="TOKEN_INVALID")
        return _unauthorized("TOKEN_INVALID")

    role = extract_role_from_claims(claims)
    if role not in {"COORDINADOR", "DIRECTOR"}:
        log_event(
            "chat_forbidden",
            request_id=request_id,
            user_role=role,
            detail="ROLE_NOT_ALLOWED",
        )
        return _forbidden("ROLE_NOT_ALLOWED")

    conversation_id = payload.conversation_id or str(uuid4())
    set_token(token)

    initial_state = {
        "messages": _build_messages(role, payload),
        "user_role": role,
        "conversation_id": conversation_id,
        "tools_used": [],
    }

    log_event(
        "chat_processing_started",
        request_id=request_id,
        conversation_id=conversation_id,
        user_role=role,
    )
    try:
        graph = get_graph()
        result = graph.invoke(
            initial_state,
            config={"recursion_limit": settings.MAX_ITERATIONS},
        )
        result_messages: list[BaseMessage] = result["messages"]
        last_message = result_messages[-1]
        text = extract_text_content(last_message.content)
        tools_used = _extract_executed_tools(result_messages)

        response = ChatResponse(
            response=text,
            conversation_id=result.get("conversation_id", conversation_id),
            tools_used=tools_used,
        )
        log_event(
            "chat_processing_completed",
            request_id=request_id,
            conversation_id=response.conversation_id,
            user_role=role,
            tools_used=response.tools_used,
        )
        return response
    except LLMServiceUnavailableError:
        log_event(
            "chat_llm_unavailable",
            level="ERROR",
            request_id=request_id,
            conversation_id=conversation_id,
            user_role=role,
            error_code="LLM_UNAVAILABLE",
        )
        return JSONResponse(
            status_code=503,
            content={
                "detail": "LLM_UNAVAILABLE",
                "message": "El servicio de IA no esta disponible temporalmente.",
            },
        )
    except Exception:
        log_event(
            "chat_processing_error",
            level="ERROR",
            request_id=request_id,
            conversation_id=conversation_id,
            user_role=role,
            error_code="CHAT_PROCESSING_ERROR",
        )
        return JSONResponse(
            status_code=500,
            content={
                "detail": "CHAT_PROCESSING_ERROR",
                "message": "No fue posible procesar la solicitud.",
            },
        )
