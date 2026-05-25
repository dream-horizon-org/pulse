"""Interaction Research agent — bounded tools, InteractionResearchV1 output."""

from __future__ import annotations

import json
import logging

from dotenv import load_dotenv
from google.adk.agents.callback_context import CallbackContext
from google.adk.agents.llm_agent import LlmAgent

from pulse_ai.constants import AGENT_MODEL, INTERACTION_RESEARCH_AGENT_NAME
from pulse_ai.schemas.interaction_research_v1 import InteractionResearchV1
from pulse_ai.agents.interaction_research.enrich import enrich_interaction_research
from pulse_ai.agents.interaction_research.prompts import build_interaction_research_prompt
from pulse_ai.agents.interaction_research.tools import (
    INTERACTION_RESEARCH_TOOL_NAMES,
    fetch_bad_interaction_sessions,
    fetch_interaction_config,
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


def _interaction_research_after_agent(callback_context: CallbackContext) -> None:
    """Re-apply deterministic segment highlights and health hints from tool payloads."""
    state = callback_context.state
    raw = state.get("interaction_research_v1")
    if raw is None:
        return
    try:
        if isinstance(raw, str):
            research = InteractionResearchV1.model_validate_json(raw)
        elif isinstance(raw, dict):
            research = InteractionResearchV1.model_validate(raw)
        else:
            research = InteractionResearchV1.model_validate(raw)
    except Exception:
        logger.warning("interaction_research_v1 state not valid; skipping enrich", exc_info=True)
        return
    enriched = enrich_interaction_research(research)
    state["interaction_research_v1"] = enriched.model_dump(mode="json")


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
        fetch_bad_interaction_sessions,
    ],
    output_schema=InteractionResearchV1,
    output_key="interaction_research_v1",
    after_agent_callback=_interaction_research_after_agent,
    include_contents="default",
)

# Documented tool list for tests and issue-03 acceptance (no unbounded tool access).
DOCUMENTED_INTERACTION_RESEARCH_TOOLS = INTERACTION_RESEARCH_TOOL_NAMES
