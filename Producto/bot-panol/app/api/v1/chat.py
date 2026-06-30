import ast
import json
from typing import Any, Literal
from uuid import uuid4

from fastapi import APIRouter, Header
from fastapi.responses import JSONResponse
from langchain_core.messages import AIMessage, BaseMessage, HumanMessage, SystemMessage, ToolMessage
from pydantic import BaseModel, Field

from app.agent.graph import get_graph
from app.agent.nodes import (
    LLMRateLimitedError,
    LLMServiceUnavailableError,
    LLMTimeoutError,
    extract_text_content,
)
from app.agent.response_sanitizer import sanitize_response_text
from app.agent.policy import evaluate_message_policy
from app.agent.prompts import build_system_prompt
from app.auth.jwt_auth import (
    TokenValidationError,
    extract_bearer_token,
    extract_role_from_claims,
    verify_and_decode_jwt,
)
from app.client.context import get_request_id, set_query_intent, set_token, set_user_role, set_user_uuid
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
    ui_blocks: list[dict[str, Any]] = Field(default_factory=list)


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


def _build_policy_source_text(payload: ChatRequest) -> str:
    relevant_history = [item.content for item in payload.history[-5:] if item.role == "user"]
    relevant_history.append(payload.message)
    return " ".join(relevant_history)


def _parse_tool_payload(message: ToolMessage) -> dict[str, Any] | None:
    content = message.content
    if isinstance(content, list):
        content = extract_text_content(content)
    if isinstance(content, dict):
        return content
    if not isinstance(content, str):
        return None

    for parser in (json.loads, ast.literal_eval):
        try:
            payload = parser(content)
        except (ValueError, SyntaxError, json.JSONDecodeError):
            continue
        if isinstance(payload, dict):
            return payload
    return None


def _collect_ui_blocks(messages: list[BaseMessage]) -> list[dict[str, Any]]:
    ui_blocks: list[dict[str, Any]] = []
    seen: set[str] = set()

    for message in messages:
        if not isinstance(message, ToolMessage):
            continue
        payload = _parse_tool_payload(message)
        if not isinstance(payload, dict):
            continue
        presentation = payload.get("presentation")
        if not isinstance(presentation, dict):
            continue
        blocks = presentation.get("ui_blocks")
        if not isinstance(blocks, list):
            continue
        for block in blocks:
            if not isinstance(block, dict):
                continue
            serialized = json.dumps(block, sort_keys=True, ensure_ascii=True)
            if serialized in seen:
                continue
            seen.add(serialized)
            ui_blocks.append(block)

    return ui_blocks


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
    user_uuid = str(claims.get("sub") or "").strip()
    set_token(token)
    set_user_role(role)
    set_user_uuid(user_uuid)

    policy_decision = evaluate_message_policy(role, _build_policy_source_text(payload))
    set_query_intent(policy_decision.intent)
    if not policy_decision.allowed:
        log_event(
            "chat_policy_blocked",
            request_id=request_id,
            conversation_id=conversation_id,
            user_role=role,
            policy_code=policy_decision.code,
        )
        return ChatResponse(
            response=policy_decision.message or "No puedo responder esa solicitud dentro del alcance permitido.",
            conversation_id=conversation_id,
            tools_used=[],
            ui_blocks=[],
        )

    initial_state = {
        "messages": _build_messages(role, payload),
        "user_role": role,
        "user_uuid": user_uuid,
        "conversation_id": conversation_id,
        "query_intent": policy_decision.intent,
        "tools_used": [],
    }

    log_event(
        "chat_processing_started",
        request_id=request_id,
        conversation_id=conversation_id,
        user_role=role,
    )
    try:
        graph = get_graph(role)
        result = graph.invoke(
            initial_state,
            config={"recursion_limit": settings.MAX_ITERATIONS},
        )
        result_messages: list[BaseMessage] = result["messages"]
        last_message = result_messages[-1]
        allow_identifiers = policy_decision.intent == "identifier_request" and role == "COORDINADOR"
        ui_blocks = _collect_ui_blocks(result_messages)
        text = sanitize_response_text(
            extract_text_content(last_message.content),
            allow_identifiers=allow_identifiers,
            ui_blocks=ui_blocks,
        )
        tools_used = _extract_executed_tools(result_messages)

        response = ChatResponse(
            response=text,
            conversation_id=result.get("conversation_id", conversation_id),
            tools_used=tools_used,
            ui_blocks=ui_blocks,
        )
        log_event(
            "chat_processing_completed",
            request_id=request_id,
            conversation_id=response.conversation_id,
            user_role=role,
            tools_used=response.tools_used,
        )
        return response
    except LLMTimeoutError as error:
        log_event(
            "chat_llm_timeout",
            level="ERROR",
            request_id=request_id,
            conversation_id=conversation_id,
            user_role=role,
            error_code="LLM_TIMEOUT",
            stage=error.stage,
            provider_error_type=error.provider_error_type,
            provider_error_message=error.provider_error_message,
            provider_status_code=error.provider_status_code,
        )
        return JSONResponse(
            status_code=504,
            content={
                "detail": "LLM_TIMEOUT",
                "message": "El asistente esta demorando mas de lo esperado. Intenta nuevamente en unos segundos.",
            },
        )
    except LLMRateLimitedError as error:
        log_event(
            "chat_llm_rate_limited",
            level="ERROR",
            request_id=request_id,
            conversation_id=conversation_id,
            user_role=role,
            error_code="LLM_RATE_LIMITED",
            stage=error.stage,
            provider_error_type=error.provider_error_type,
            provider_error_message=error.provider_error_message,
            provider_status_code=error.provider_status_code,
        )
        return JSONResponse(
            status_code=429,
            content={
                "detail": "LLM_RATE_LIMITED",
                "message": "El asistente esta recibiendo demasiadas solicitudes en este momento. Intenta nuevamente en unos segundos.",
            },
        )
    except LLMServiceUnavailableError as error:
        log_event(
            "chat_llm_unavailable",
            level="ERROR",
            request_id=request_id,
            conversation_id=conversation_id,
            user_role=role,
            error_code="LLM_UNAVAILABLE",
            stage=error.stage,
            provider_error_type=error.provider_error_type,
            provider_error_message=error.provider_error_message,
            provider_status_code=error.provider_status_code,
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
