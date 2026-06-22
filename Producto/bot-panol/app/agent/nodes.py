from typing import Any

from langchain_core.messages import AIMessage, BaseMessage, HumanMessage
from langchain_google_genai import ChatGoogleGenerativeAI
from time import perf_counter

from app.agent.state import AgentState
from app.client.context import get_user_role
from app.config import settings
from app.observability.logger import log_event
from app.observability.metrics import record_llm_call
from app.tools import get_tools


class LLMServiceUnavailableError(RuntimeError):
    pass


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


def _invoke_llm(messages: list[BaseMessage], *, with_tools: bool, conversation_id: str | None, event_name: str) -> AIMessage:
    started_at = perf_counter()
    try:
        llm = _build_model(with_tools=with_tools)
        response = llm.invoke(messages)
        elapsed = perf_counter() - started_at
        record_llm_call(settings.GEMINI_MODEL, "success", elapsed)
        log_event(
            event_name,
            model=settings.GEMINI_MODEL,
            conversation_id=conversation_id,
            latency_ms=round(elapsed * 1000, 2),
        )
        return response
    except LLMServiceUnavailableError:
        elapsed = perf_counter() - started_at
        record_llm_call(settings.GEMINI_MODEL, "error", elapsed)
        raise
    except Exception as exc:
        elapsed = perf_counter() - started_at
        record_llm_call(settings.GEMINI_MODEL, "error", elapsed)
        log_event(
            f"{event_name}_error",
            level="ERROR",
            model=settings.GEMINI_MODEL,
            conversation_id=conversation_id,
            latency_ms=round(elapsed * 1000, 2),
        )
        raise LLMServiceUnavailableError("LLM provider is unavailable.") from exc


def call_model(state: AgentState) -> dict[str, list[AIMessage]]:
    response = _invoke_llm(
        state["messages"],
        with_tools=True,
        conversation_id=state.get("conversation_id"),
        event_name="llm_call_success",
    )
    return {"messages": [response]}


def finalize_response(state: AgentState) -> dict[str, list[AIMessage]]:
    conversation_id = state.get("conversation_id")
    messages = list(state["messages"])

    if messages and isinstance(messages[-1], AIMessage) and not messages[-1].tool_calls and not has_meaningful_text(messages[-1]):
        messages = messages[:-1]

    finalization_instruction = HumanMessage(
        content=(
            "Redacta ahora la respuesta final para el usuario en espanol, usando solo la informacion ya obtenida. "
            "No llames herramientas ni dejes la respuesta vacia."
        )
    )
    response = _invoke_llm(
        [*messages, finalization_instruction],
        with_tools=False,
        conversation_id=conversation_id,
        event_name="llm_finalization_success",
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
    )
    return {"messages": [retry_response]}
