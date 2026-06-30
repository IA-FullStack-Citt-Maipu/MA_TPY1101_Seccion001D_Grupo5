from concurrent.futures import TimeoutError as FutureTimeoutError

import pytest
from langchain_core.messages import AIMessage, HumanMessage

import app.agent.nodes as nodes_module
from app.agent.nodes import LLMRateLimitedError, LLMServiceUnavailableError, LLMTimeoutError
from app.config import settings


def test_invoke_llm_raises_total_timeout_when_deadline_is_exceeded(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    monkeypatch.setattr(settings, "GOOGLE_API_KEY", "test-key")
    monkeypatch.setattr(settings, "LLM_TOTAL_TIMEOUT_SECONDS", 75.0)

    class FakeFuture:
        def result(self, timeout=None):
            raise FutureTimeoutError()

        def cancel(self):
            return True

    class FakeExecutor:
        def submit(self, fn, *args, **kwargs):
            return FakeFuture()

        def shutdown(self, wait=False, cancel_futures=True):
            return None

    class FakeModel:
        def invoke(self, messages):
            return AIMessage(content="should-not-complete")

    monkeypatch.setattr(nodes_module, "ThreadPoolExecutor", lambda **kwargs: FakeExecutor())
    monkeypatch.setattr(nodes_module, "_build_model", lambda with_tools: FakeModel())

    with pytest.raises(LLMTimeoutError) as error:
        nodes_module._invoke_llm(
            [HumanMessage(content="hola")],
            with_tools=False,
            conversation_id="conversation-1",
            event_name="llm_call_success",
            stage="agent",
        )

    assert error.value.stage == "agent"
    assert error.value.provider_error_message == "LLM call exceeded 75.0 seconds"


def test_invoke_llm_maps_provider_429_to_rate_limited(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    monkeypatch.setattr(settings, "GOOGLE_API_KEY", "test-key")

    class FakeModel:
        def invoke(self, messages):
            class RateLimitError(Exception):
                status_code = 429

            raise RateLimitError("too many requests")

    monkeypatch.setattr(nodes_module, "_build_model", lambda with_tools: FakeModel())

    with pytest.raises(LLMRateLimitedError) as error:
        nodes_module._invoke_llm(
            [HumanMessage(content="hola")],
            with_tools=False,
            conversation_id="conversation-2",
            event_name="llm_call_success",
            stage="finalizer",
        )

    assert error.value.stage == "finalizer"
    assert error.value.provider_status_code == 429
    assert error.value.provider_error_type == "RateLimitError"


def test_invoke_llm_maps_generic_provider_error_to_unavailable(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    monkeypatch.setattr(settings, "GOOGLE_API_KEY", "test-key")

    class FakeModel:
        def invoke(self, messages):
            raise RuntimeError("provider down")

    monkeypatch.setattr(nodes_module, "_build_model", lambda with_tools: FakeModel())

    with pytest.raises(LLMServiceUnavailableError) as error:
        nodes_module._invoke_llm(
            [HumanMessage(content="hola")],
            with_tools=False,
            conversation_id="conversation-3",
            event_name="llm_finalization_success",
            stage="finalizer",
        )

    assert error.value.stage == "finalizer"
    assert error.value.provider_error_type == "RuntimeError"
    assert error.value.provider_error_message == "provider down"


def test_invoke_llm_returns_response_when_provider_succeeds(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    monkeypatch.setattr(settings, "GOOGLE_API_KEY", "test-key")

    class FakeModel:
        def invoke(self, messages):
            return AIMessage(content="ok")

    monkeypatch.setattr(nodes_module, "_build_model", lambda with_tools: FakeModel())

    response = nodes_module._invoke_llm(
        [HumanMessage(content="hola")],
        with_tools=False,
        conversation_id="conversation-4",
        event_name="llm_call_success",
        stage="agent",
    )

    assert isinstance(response, AIMessage)
    assert response.content == "ok"


def test_build_finalization_instruction_mentions_structured_blocks_when_present() -> None:
    instruction = nodes_module._build_finalization_instruction(
        query_intent="inventory_summary",
        has_structured_ui_blocks=True,
    )

    assert "bloques estructurados" in instruction.content.lower()
    assert "sin repetir titulos" in instruction.content.lower()
