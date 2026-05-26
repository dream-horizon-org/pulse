"""Interaction Research agent — bounded tools, InteractionResearchV1 output."""

from __future__ import annotations

import json
import logging

from dotenv import load_dotenv
from google.adk.agents.callback_context import CallbackContext
from google.adk.agents.llm_agent import LlmAgent

from pulse_ai.constants import AGENT_MODEL, INTERACTION_RESEARCH_AGENT_NAME
from pulse_ai.schemas.interaction_research_v1 import (
    InteractionResearchV1,
    InteractionResearchV1Llm,
    research_from_llm_output,
)
from pulse_ai.agents.interaction_research.enrich import enrich_interaction_research
from pulse_ai.agents.interaction_research.prompts import build_interaction_research_prompt
from pulse_ai.agents.interaction_research.tool_payload_state import (
    INTERACTION_RESEARCH_TOOL_PAYLOADS_KEY,
    capture_tool_response,
    format_tool_log_args,
    format_tool_log_response,
)
from pulse_ai.agents.interaction_research.tools import (
    INTERACTION_RESEARCH_TOOL_NAMES,
    breakdown_interaction_by_dimension,
    fetch_interaction_config,
    fetch_interaction_latency_percentiles,
    fetch_interaction_metric_trends,
    fetch_problematic_interaction_spans,
    fetch_session_trace_snapshot,
    fetch_interaction_metrics,
    fetch_interaction_root_cause_segments,
    get_funnel,
    get_journey,
    list_funnels,
    list_journeys,
    search_event_catalog,
)

logger = logging.getLogger(__name__)

load_dotenv()


def _interaction_research_after_tool(
    tool: object,
    args: dict[str, object],
    tool_context: object,
    tool_response: object,
) -> None:
    """Store successful tool responses for deterministic merge after the LLM step."""
    state = getattr(tool_context, "state", None)
    if not isinstance(state, dict):
        return
    tool_name = getattr(tool, "name", None) or getattr(getattr(tool, "func", tool), "__name__", "unknown")
    args_text = format_tool_log_args(args)
    response_text = format_tool_log_response(tool_response)
    if isinstance(tool_response, dict):
        status = tool_response.get("status", "ok")
        log_line = (
            "Interaction research tool complete tool=%s status=%s args=%s response=%s"
        )
        log_args = (tool_name, status, args_text, response_text)
        if status == "error":
            logger.warning(log_line, *log_args)
        else:
            logger.info(log_line, *log_args)
    else:
        logger.info(
            "Interaction research tool complete tool=%s args=%s response=%s",
            tool_name,
            args_text,
            response_text,
        )
    capture_tool_response(tool=tool, tool_response=tool_response, state=state)


def _interaction_research_after_agent(callback_context: CallbackContext) -> None:
    """Merge tool captures, then apply segment highlights and health hints."""
    state = callback_context.state
    raw = state.get("interaction_research_v1")
    if raw is None:
        logger.warning("Interaction research agent finished without interaction_research_v1 in state")
        return
    tool_payloads = state.get(INTERACTION_RESEARCH_TOOL_PAYLOADS_KEY)
    try:
        research = research_from_llm_output(
            raw,
            tool_payloads=tool_payloads if isinstance(tool_payloads, dict) else None,
        )
    except Exception:
        logger.warning("interaction_research_v1 state not valid; skipping enrich", exc_info=True)
        return
    enriched = enrich_interaction_research(research)
    state["interaction_research_v1"] = enriched.model_dump(mode="json")
    captured_tools = sorted(tool_payloads.keys()) if isinstance(tool_payloads, dict) else []
    logger.info(
        "Interaction research agent complete health_rating=%s paradox=%s captured_tools=%s",
        enriched.health_rating,
        enriched.paradox_kpi_hint is not None,
        captured_tools,
    )


def _interaction_research_before_agent(callback_context: CallbackContext) -> None:
    """Clear per-run tool captures."""
    callback_context.state[INTERACTION_RESEARCH_TOOL_PAYLOADS_KEY] = {}
    logger.info("Interaction research agent start")


interaction_research_agent = LlmAgent(
    model=AGENT_MODEL,
    name=INTERACTION_RESEARCH_AGENT_NAME,
    description=(
        "Interaction Research agent: calls bounded Pulse REST tools and writes "
        "interaction_research_v1 for the health report pipeline."
    ),
    instruction=build_interaction_research_prompt,
    tools=[
        fetch_interaction_config,
        fetch_interaction_metrics,
        fetch_interaction_root_cause_segments,
        list_journeys,
        get_journey,
        list_funnels,
        get_funnel,
        search_event_catalog,
        fetch_problematic_interaction_spans,
        fetch_session_trace_snapshot,
        fetch_interaction_metric_trends,
        fetch_interaction_latency_percentiles,
        breakdown_interaction_by_dimension,
    ],
    output_schema=InteractionResearchV1Llm,
    output_key="interaction_research_v1",
    before_agent_callback=_interaction_research_before_agent,
    after_tool_callback=_interaction_research_after_tool,
    after_agent_callback=_interaction_research_after_agent,
    include_contents="default",
)

# Documented tool list for tests and issue-03 acceptance (no unbounded tool access).
DOCUMENTED_INTERACTION_RESEARCH_TOOLS = INTERACTION_RESEARCH_TOOL_NAMES
