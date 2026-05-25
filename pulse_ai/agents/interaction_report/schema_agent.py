"""Interaction Report Schema agent (Agent 2) — full InteractionReportV1, no tools."""

from __future__ import annotations

from dotenv import load_dotenv
from google.adk.agents.llm_agent import LlmAgent

from pulse_ai.constants import AGENT_MODEL, INTERACTION_REPORT_SCHEMA_AGENT_NAME
from pulse_ai.schemas.interaction_report_v1 import InteractionReportV1

from .prompts import build_interaction_report_schema_prompt

load_dotenv()

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
    include_contents="default",
)
