"""Sequential pipeline: Interaction Research (Agent 1) → Report Schema (Agent 2)."""

from __future__ import annotations

from google.adk.agents.sequential_agent import SequentialAgent

from pulse_ai.agents.interaction_research.agent import interaction_research_agent
from pulse_ai.constants import INTERACTION_REPORT_PIPELINE_NAME

from .schema_agent import interaction_report_schema_agent

interaction_report_pipeline = SequentialAgent(
    name=INTERACTION_REPORT_PIPELINE_NAME,
    sub_agents=[interaction_research_agent, interaction_report_schema_agent],
    description=(
        "Per-interaction health report: Research agent (tools) → Schema agent "
        "(full InteractionReportV1)."
    ),
)
