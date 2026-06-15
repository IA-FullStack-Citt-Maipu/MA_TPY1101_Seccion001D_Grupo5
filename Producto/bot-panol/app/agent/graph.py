from typing import Any

from langchain_core.messages import AIMessage
from langgraph.graph import END, StateGraph
from langgraph.prebuilt import ToolNode

from app.agent.nodes import call_model, finalize_response, has_meaningful_text
from app.agent.state import AgentState
from app.observability.logger import log_event
from app.tools import get_tools


def _route_after_agent(state: AgentState) -> str:
    messages = state.get("messages", [])
    if not messages:
        return "__end__"

    last_message = messages[-1]
    if not isinstance(last_message, AIMessage):
        return "__end__"

    if last_message.tool_calls:
        return "tools"

    if not has_meaningful_text(last_message):
        return "finalizer"

    return "__end__"


def build_graph(tools: list[Any] | None = None) -> Any:
    selected_tools = list(tools) if tools is not None else list(get_tools())
    log_event("graph_build_started", tools_count=len(selected_tools))

    graph = StateGraph(AgentState)
    graph.add_node("agent", call_model)
    graph.add_node("tools", ToolNode(selected_tools))
    graph.add_node("finalizer", finalize_response)
    graph.set_entry_point("agent")
    graph.add_conditional_edges(
        "agent",
        _route_after_agent,
        {
            "tools": "tools",
            "finalizer": "finalizer",
            "__end__": END,
        },
    )
    graph.add_edge("tools", "agent")
    graph.add_edge("finalizer", END)
    compiled = graph.compile()
    log_event("graph_build_completed", tools_count=len(selected_tools))
    return compiled


_GRAPH = build_graph()


def get_graph() -> Any:
    return _GRAPH
