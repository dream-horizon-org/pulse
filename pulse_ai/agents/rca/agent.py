"""RCA (Root Cause Analysis) agent and pipeline.

Pipeline:
  1. rca_agent       — pure reasoning, outputs analysis text → output_key="rca_analysis_result"
  2. rca_formatter   — no tools, output_schema=RcaStructuredReportV1 → output_key="rca_structured_report"
"""

from google.adk.agents.llm_agent import LlmAgent
from google.adk.agents.sequential_agent import SequentialAgent

from pulse_ai.constants import (
    AGENT_MODEL,
    RCA_ANALYZER_AGENT_NAME,
    RCA_FORMATTER_AGENT_NAME,
    RCA_PIPELINE_AGENT_NAME,
)
from pulse_ai.schemas.rca_structured_v1 import RcaStructuredReportV1
from .prompts import build_rca_formatter_prompt, build_rca_prompt

rca_agent = LlmAgent(
    model=AGENT_MODEL,
    name=RCA_ANALYZER_AGENT_NAME,
    description="Root Cause Analysis agent that identifies correlations and anomalies in hierarchical segment data.",
    instruction=build_rca_prompt,
    tools=[],  # Pure reasoning agent — no tools needed
    output_key="rca_analysis_result",
)

# Formatter: no tools — output_schema forces structured JSON output (ADK constraint).
# Reads rca_analysis_result from session state via build_rca_formatter_prompt.
rca_formatter_agent = LlmAgent(
    model=AGENT_MODEL,
    name=RCA_FORMATTER_AGENT_NAME,
    description="Converts RCA analysis text into a validated RcaStructuredReportV1 JSON object.",
    instruction=build_rca_formatter_prompt,
    tools=[],
    output_schema=RcaStructuredReportV1,
    output_key="rca_structured_report",
    include_contents="default",  # Needs full history to access original RootCausePayload for all metrics
)

rca_pipeline_agent = SequentialAgent(
    name=RCA_PIPELINE_AGENT_NAME,
    sub_agents=[rca_agent, rca_formatter_agent],
    description=(
        "Sequential pipeline: RCA Analyzer (root cause analysis) → "
        "RCA Formatter (structured JSON output)"
    ),
)
