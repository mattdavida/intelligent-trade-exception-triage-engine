"""
Single-node LangGraph: propose severity + recommendation + reasoning.

Does NOT emit confidence — that is owned by the Java orchestrator rubric.
Does NOT recompute amounts or invent trade IDs.
"""

from __future__ import annotations

from typing import TypedDict

from langchain_core.messages import HumanMessage, SystemMessage
from langgraph.graph import END, START, StateGraph

from llm import get_chat_llm
from models import AnalyzeExceptionRequest, AnalyzeExceptionResponse, Severity


class AnalyzeState(TypedDict):
    request: AnalyzeExceptionRequest
    result: AnalyzeExceptionResponse | None


SYSTEM_PROMPT = """You are a middle-office trade exception analyst assistant.

Given a trade exception event, return structured JSON with:
- severity: HIGH | MEDIUM | LOW
- recommendation: a short operational next step for the ops desk
- reasoning: why that severity and recommendation fit the facts

Hard rules:
- Use ONLY the facts in the payload. Do not invent trade IDs, amounts, or counterparties.
- Do NOT recompute or alter the amount.
- Do NOT output a confidence score (confidence is computed elsewhere).
- Prefer actionable, control-aware language (hold settlement, confirm SSI, escalate, etc.).
- Severity guidance (heuristic, not a formula):
  - HIGH: SSI/settlement breaks, large notional (>= 1M), unknown counterparty, duplicates
  - MEDIUM: quantity/price/currency mismatches, late confirms, missing LEI
  - LOW: minor/incomplete issues with clear remediation
"""


def _analyze_node(state: AnalyzeState) -> AnalyzeState:
    req = state["request"]
    llm = get_chat_llm().with_structured_output(AnalyzeExceptionResponse)

    payload = (
        f"tradeId: {req.tradeId}\n"
        f"counterparty: {req.counterparty}\n"
        f"discrepancyType: {req.discrepancyType}\n"
        f"instrument: {req.instrument}\n"
        f"amount: {req.amount} {req.currency}\n"
        f"side: {req.side}\n"
        f"detectedAt: {req.detectedAt.isoformat()}\n"
        f"rawDetails: {req.rawDetails}\n"
    )

    result = llm.invoke(
        [
            SystemMessage(content=SYSTEM_PROMPT),
            HumanMessage(content=payload),
        ]
    )

    if not isinstance(result, AnalyzeExceptionResponse):
        # Defensive: some versions return dict
        result = AnalyzeExceptionResponse.model_validate(result)

    # Normalize severity enum if model returned stringy values
    if isinstance(result.severity, str):
        result.severity = Severity(result.severity)

    return {"request": req, "result": result}


def build_graph():
    graph = StateGraph(AnalyzeState)
    graph.add_node("analyze", _analyze_node)
    graph.add_edge(START, "analyze")
    graph.add_edge("analyze", END)
    return graph.compile()


_GRAPH = None


def get_graph():
    global _GRAPH
    if _GRAPH is None:
        _GRAPH = build_graph()
    return _GRAPH


def analyze_exception(request: AnalyzeExceptionRequest) -> AnalyzeExceptionResponse:
    out = get_graph().invoke({"request": request, "result": None})
    result = out.get("result")
    if result is None:
        raise RuntimeError("Analyze graph returned no result")
    return result
