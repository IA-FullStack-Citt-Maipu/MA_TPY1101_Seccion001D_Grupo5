import pytest
from langchain_core.messages import AIMessage, HumanMessage, ToolMessage
from langchain_core.tools import tool

import app.agent.graph as graph_module
from app.agent.nodes import LLMServiceUnavailableError


def _initial_state() -> dict:
    return {
        "messages": [HumanMessage(content="hola")],
        "user_role": "COORDINADOR",
        "conversation_id": "test-conversation",
        "tools_used": [],
    }


def test_graph_no_tool_call_returns_direct_response(monkeypatch) -> None:
    def fake_call_model(state):
        return {"messages": [AIMessage(content="respuesta directa")]}

    monkeypatch.setattr(graph_module, "call_model", fake_call_model)
    graph = graph_module.build_graph(tools=[])
    result = graph.invoke(_initial_state(), config={"recursion_limit": 6})

    assert isinstance(result["messages"][-1], AIMessage)
    assert result["messages"][-1].content == "respuesta directa"


def test_graph_simple_tool_call_executes_tool(monkeypatch) -> None:
    @tool
    def buscar_implementos(nombre: str) -> str:
        """Test helper tool: buscar implementos."""
        return f"ok:{nombre}"

    counter = {"n": 0}

    def fake_call_model(state):
        counter["n"] += 1
        if counter["n"] == 1:
            return {
                "messages": [
                    AIMessage(
                        content="",
                        tool_calls=[
                            {
                                "name": "buscar_implementos",
                                "args": {"nombre": "gasa"},
                                "id": "call-1",
                                "type": "tool_call",
                            }
                        ],
                    )
                ]
            }
        return {"messages": [AIMessage(content="respuesta final")]}

    monkeypatch.setattr(graph_module, "call_model", fake_call_model)
    graph = graph_module.build_graph(tools=[buscar_implementos])
    result = graph.invoke(_initial_state(), config={"recursion_limit": 8})

    tool_messages = [m for m in result["messages"] if isinstance(m, ToolMessage)]
    assert len(tool_messages) == 1
    assert tool_messages[0].name == "buscar_implementos"
    assert result["messages"][-1].content == "respuesta final"


def test_graph_chained_tool_calls(monkeypatch) -> None:
    @tool
    def buscar_implementos(nombre: str) -> str:
        """Test helper tool: buscar implementos."""
        return f"uuid:{nombre}"

    @tool
    def consultar_stock(implement_uuid: str) -> str:
        """Test helper tool: consultar stock."""
        return f"stock:{implement_uuid}"

    counter = {"n": 0}

    def fake_call_model(state):
        counter["n"] += 1
        if counter["n"] == 1:
            return {
                "messages": [
                    AIMessage(
                        content="",
                        tool_calls=[
                            {
                                "name": "buscar_implementos",
                                "args": {"nombre": "microscopio"},
                                "id": "call-1",
                                "type": "tool_call",
                            }
                        ],
                    )
                ]
            }
        if counter["n"] == 2:
            return {
                "messages": [
                    AIMessage(
                        content="",
                        tool_calls=[
                            {
                                "name": "consultar_stock",
                                "args": {"implement_uuid": "uuid-micro"},
                                "id": "call-2",
                                "type": "tool_call",
                            }
                        ],
                    )
                ]
            }
        return {"messages": [AIMessage(content="hay stock disponible")]}

    monkeypatch.setattr(graph_module, "call_model", fake_call_model)
    graph = graph_module.build_graph(tools=[buscar_implementos, consultar_stock])
    result = graph.invoke(_initial_state(), config={"recursion_limit": 10})

    tool_messages = [m for m in result["messages"] if isinstance(m, ToolMessage)]
    tool_names = [m.name for m in tool_messages]
    assert tool_names == ["buscar_implementos", "consultar_stock"]
    assert result["messages"][-1].content == "hay stock disponible"


def test_graph_propagates_llm_unavailable_error(monkeypatch) -> None:
    def failing_call_model(state):
        raise LLMServiceUnavailableError("provider down")

    monkeypatch.setattr(graph_module, "call_model", failing_call_model)
    graph = graph_module.build_graph(tools=[])

    with pytest.raises(LLMServiceUnavailableError):
        graph.invoke(_initial_state(), config={"recursion_limit": 5})


def test_graph_finalizes_when_agent_returns_empty_text_without_tool_calls(monkeypatch) -> None:
    def fake_call_model(state):
        return {"messages": [AIMessage(content="")]}

    def fake_finalize_response(state):
        return {"messages": [AIMessage(content="respuesta final sintetizada")]}

    monkeypatch.setattr(graph_module, "call_model", fake_call_model)
    monkeypatch.setattr(graph_module, "finalize_response", fake_finalize_response)
    graph = graph_module.build_graph(tools=[])

    result = graph.invoke(_initial_state(), config={"recursion_limit": 6})

    assert isinstance(result["messages"][-1], AIMessage)
    assert result["messages"][-1].content == "respuesta final sintetizada"
