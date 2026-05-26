"""Interaction Report Schema agent (Agent 2) — full InteractionReportV1, no tools."""

from __future__ import annotations

import logging

from dotenv import load_dotenv
from google.adk.agents.callback_context import CallbackContext
from google.adk.agents.llm_agent import LlmAgent
from pydantic import ValidationError

from pulse_ai.constants import AGENT_MODEL, INTERACTION_REPORT_SCHEMA_AGENT_NAME
from pulse_ai.schemas.interaction_report_v1 import InteractionReportV1

from .prompts import build_interaction_report_schema_prompt

logger = logging.getLogger(__name__)

load_dotenv()


def _interaction_report_schema_after_agent(callback_context: CallbackContext) -> None:
    raw = callback_context.state.get("interaction_report_v1")
    if raw is None:
        logger.warning(
            "Interaction report schema agent finished without interaction_report_v1 in state",
        )
        return
    try:
        if isinstance(raw, str):
            report = InteractionReportV1.model_validate_json(raw)
        elif isinstance(raw, dict):
            report = InteractionReportV1.model_validate(raw)
        else:
            report = InteractionReportV1.model_validate(raw)
    except ValidationError:
        logger.warning(
            "Interaction report schema agent output failed validation",
            exc_info=True,
        )
        return
    logger.info(
        "Interaction report schema agent complete llm_rating=%s primary_kpi=%s action_count=%d",
        report.verdict.rating,
        report.verdict.primary_kpi.metric,
        len(report.actions),
    )


interaction_report_schema_agent = LlmAgent(
    model=AGENT_MODEL,
    name=INTERACTION_REPORT_SCHEMA_AGENT_NAME,
    description=(
        "Interaction Report Schema agent: reads interaction_research_v1 and emits "
        "full InteractionReportV1 for the health report API."
    ),
    instruction=build_interaction_report_schema_prompt,
    tools=[],
    output_schema=InteractionReportV1,
    output_key="interaction_report_v1",
    after_agent_callback=_interaction_report_schema_after_agent,
    include_contents="default",
)
