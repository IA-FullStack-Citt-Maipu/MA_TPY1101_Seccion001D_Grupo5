import ast
import json
from concurrent.futures import ThreadPoolExecutor, TimeoutError as FutureTimeoutError
from typing import Any

from langchain_core.messages import AIMessage, BaseMessage, HumanMessage
from langchain_google_genai import ChatGoogleGenerativeAI
from time import perf_counter

from app.agent.state import AgentState
from app.client.context import get_query_intent, get_user_role
from app.config import settings
from app.observability.logger import log_event
from app.observability.metrics import record_llm_call
from app.tools import get_tools


class LLMServiceUnavailableError(RuntimeError):
    def __init__(
        self,
        message: str,
        *,
        stage: str | None = None,
        provider_error_type: str | None = None,
        provider_error_message: str | None = None,
        provider_status_code: int | None = None,
    ) -> None:
        super().__init__(message)
        self.stage = stage
        self.provider_error_type = provider_error_type
        self.provider_error_message = provider_error_message
        self.provider_status_code = provider_status_code


class LLMTimeoutError(RuntimeError):
    def __init__(
        self,
        message: str,
        *,
        stage: str | None = None,
        provider_error_type: str | None = None,
        provider_error_message: str | None = None,
        provider_status_code: int | None = None,
    ) -> None:
        super().__init__(message)
        self.stage = stage
        self.provider_error_type = provider_error_type
        self.provider_error_message = provider_error_message
        self.provider_status_code = provider_status_code


class LLMRateLimitedError(RuntimeError):
    def __init__(
        self,
        message: str,
        *,
        stage: str | None = None,
        provider_error_type: str | None = None,
        provider_error_message: str | None = None,
        provider_status_code: int | None = None,
    ) -> None:
        super().__init__(message)
        self.stage = stage
        self.provider_error_type = provider_error_type
        self.provider_error_message = provider_error_message
        self.provider_status_code = provider_status_code


def _extract_provider_status_code(exc: Exception) -> int | None:
    for attribute in ("status_code", "code", "http_status"):
        value = getattr(exc, attribute, None)
        if isinstance(value, int):
            return value

    response = getattr(exc, "response", None)
    if response is not None:
        value = getattr(response, "status_code", None)
        if isinstance(value, int):
            return value

    return None


def _trim_provider_message(exc: Exception) -> str:
    message = str(exc).strip()
    if not message:
        message = exc.__class__.__name__
    return message[:500]


def _classify_llm_exception(exc: Exception, *, stage: str) -> RuntimeError:
    provider_status_code = _extract_provider_status_code(exc)
    provider_error_type = exc.__class__.__name__
    provider_error_message = _trim_provider_message(exc)
    normalized_type = provider_error_type.lower()

    if provider_status_code == 429 or normalized_type in {
        "resourceexhausted",
        "toomanyrequests",
        "ratelimiterror",
    }:
        return LLMRateLimitedError(
            "LLM provider rate limited the request.",
            stage=stage,
            provider_error_type=provider_error_type,
            provider_error_message=provider_error_message,
            provider_status_code=provider_status_code,
        )

    if provider_status_code == 504 or normalized_type in {
        "deadlineexceeded",
        "readtimeout",
        "timeout",
        "timeoutexception",
    }:
        return LLMTimeoutError(
            "LLM provider timed out.",
            stage=stage,
            provider_error_type=provider_error_type,
            provider_error_message=provider_error_message,
            provider_status_code=provider_status_code,
        )

    return LLMServiceUnavailableError(
        "LLM provider is unavailable.",
        stage=stage,
        provider_error_type=provider_error_type,
        provider_error_message=provider_error_message,
        provider_status_code=provider_status_code,
    )


def _invoke_with_total_timeout(llm: object, messages: list[BaseMessage], *, stage: str) -> AIMessage:
    executor = ThreadPoolExecutor(max_workers=1, thread_name_prefix=f"llm-{stage}")
    future = executor.submit(llm.invoke, messages)
    try:
        return future.result(timeout=settings.LLM_TOTAL_TIMEOUT_SECONDS)
    except FutureTimeoutError as exc:
        future.cancel()
        raise LLMTimeoutError(
            "LLM total timeout exceeded.",
            stage=stage,
            provider_error_type=exc.__class__.__name__,
            provider_error_message=(
                f"LLM call exceeded {settings.LLM_TOTAL_TIMEOUT_SECONDS} seconds"
            ),
        ) from exc
    finally:
        executor.shutdown(wait=False, cancel_futures=True)


def _build_model(*, with_tools: bool) -> object:
    if not settings.GOOGLE_API_KEY:
        raise LLMServiceUnavailableError("GOOGLE_API_KEY is missing.")

    llm = ChatGoogleGenerativeAI(
        model=settings.GEMINI_MODEL,
        google_api_key=settings.GOOGLE_API_KEY,
        temperature=0.1,
        timeout=settings.LLM_TIMEOUT_SECONDS,
    )
    if not with_tools:
        return llm

    role = get_user_role()
    tools = list(get_tools(role))
    return llm.bind_tools(tools)


def extract_text_content(content: Any) -> str:
    if isinstance(content, str):
        return content.strip()

    if isinstance(content, list):
        chunks: list[str] = []
        for item in content:
            if isinstance(item, str):
                text = item.strip()
                if text:
                    chunks.append(text)
                continue
            if isinstance(item, dict):
                text = item.get("text")
                if isinstance(text, str) and text.strip():
                    chunks.append(text.strip())
        return "\n".join(chunks).strip()

    return ""


def has_meaningful_text(message: BaseMessage) -> bool:
    return bool(extract_text_content(message.content))


def _parse_tool_payload(content: Any) -> dict[str, Any] | None:
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


def _has_structured_ui_blocks(messages: list[BaseMessage]) -> bool:
    for message in messages:
        tool_name = getattr(message, "name", None)
        if tool_name is None:
            continue
        payload = _parse_tool_payload(getattr(message, "content", None))
        if not isinstance(payload, dict):
            continue
        presentation = payload.get("presentation")
        if not isinstance(presentation, dict):
            continue
        blocks = presentation.get("ui_blocks")
        if isinstance(blocks, list) and blocks:
            return True
    return False


def _build_finalization_instruction(*, query_intent: str | None, has_structured_ui_blocks: bool) -> HumanMessage:
    finalization_rule = (
        "Si la consulta fue un pedido explicito de identificador tecnico y las herramientas entregaron una seccion tecnica valida, "
        "puedes incluir ese identificador de forma breve. "
        if query_intent == "identifier_request"
        else "No reveles identificadores tecnicos ni UUIDs. "
    )
    structured_rule = (
        "La interfaz mostrara bloques estructurados aparte. Redacta solo una breve introduccion o conclusion operativa, "
        "sin repetir titulos, listas, metricas ni elementos que ya vengan en esos bloques. "
        if has_structured_ui_blocks
        else ""
    )
    return HumanMessage(
        content=(
            "Redacta ahora la respuesta final para el usuario en espanol, usando solo la informacion ya obtenida. "
            + finalization_rule
            + structured_rule
            + "No llames herramientas ni dejes la respuesta vacia."
        )
    )


def _invoke_llm(
    messages: list[BaseMessage],
    *,
    with_tools: bool,
    conversation_id: str | None,
    event_name: str,
    stage: str,
) -> AIMessage:
    started_at = perf_counter()
    try:
        llm = _build_model(with_tools=with_tools)
        response = _invoke_with_total_timeout(llm, messages, stage=stage)
        elapsed = perf_counter() - started_at
        record_llm_call(settings.GEMINI_MODEL, "success", elapsed)
        log_event(
            event_name,
            model=settings.GEMINI_MODEL,
            conversation_id=conversation_id,
            stage=stage,
            latency_ms=round(elapsed * 1000, 2),
        )
        return response
    except (LLMTimeoutError, LLMRateLimitedError, LLMServiceUnavailableError) as exc:
        elapsed = perf_counter() - started_at
        if isinstance(exc, LLMTimeoutError):
            outcome = "timeout"
        elif isinstance(exc, LLMRateLimitedError):
            outcome = "rate_limited"
        else:
            outcome = "unavailable"
        record_llm_call(settings.GEMINI_MODEL, outcome, elapsed)
        log_event(
            f"{event_name}_error",
            level="ERROR",
            model=settings.GEMINI_MODEL,
            conversation_id=conversation_id,
            stage=stage,
            latency_ms=round(elapsed * 1000, 2),
            provider_error_type=getattr(exc, "provider_error_type", None),
            provider_error_message=getattr(exc, "provider_error_message", None),
            provider_status_code=getattr(exc, "provider_status_code", None),
        )
        raise
    except Exception as exc:
        elapsed = perf_counter() - started_at
        record_llm_call(settings.GEMINI_MODEL, "error", elapsed)
        classified_error = _classify_llm_exception(exc, stage=stage)
        log_event(
            f"{event_name}_error",
            level="ERROR",
            model=settings.GEMINI_MODEL,
            conversation_id=conversation_id,
            stage=stage,
            latency_ms=round(elapsed * 1000, 2),
            provider_error_type=classified_error.provider_error_type,
            provider_error_message=classified_error.provider_error_message,
            provider_status_code=classified_error.provider_status_code,
        )
        raise classified_error from exc


def call_model(state: AgentState) -> dict[str, list[AIMessage]]:
    response = _invoke_llm(
        state["messages"],
        with_tools=True,
        conversation_id=state.get("conversation_id"),
        event_name="llm_call_success",
        stage="agent",
    )
    return {"messages": [response]}


def finalize_response(state: AgentState) -> dict[str, list[AIMessage]]:
    conversation_id = state.get("conversation_id")
    messages = list(state["messages"])
    query_intent = get_query_intent()
    has_structured_ui_blocks = _has_structured_ui_blocks(messages)

    if messages and isinstance(messages[-1], AIMessage) and not messages[-1].tool_calls and not has_meaningful_text(messages[-1]):
        messages = messages[:-1]

    finalization_instruction = _build_finalization_instruction(
        query_intent=query_intent,
        has_structured_ui_blocks=has_structured_ui_blocks,
    )
    response = _invoke_llm(
        [*messages, finalization_instruction],
        with_tools=False,
        conversation_id=conversation_id,
        event_name="llm_finalization_success",
        stage="finalizer",
    )

    if has_meaningful_text(response):
        return {"messages": [response]}

    retry_instruction = HumanMessage(
        content=(
            "La respuesta anterior quedo vacia. Responde ahora con texto claro y breve para el usuario, "
            "basado solo en los resultados disponibles."
        )
    )
    retry_response = _invoke_llm(
        [*messages, finalization_instruction, retry_instruction],
        with_tools=False,
        conversation_id=conversation_id,
        event_name="llm_finalization_retry_success",
        stage="finalizer_retry",
    )
    return {"messages": [retry_response]}
