"""RCA (Root Cause Analysis) agent.

Single-agent architecture:
  - Analyzes segment data and produces structured JSON output directly
  - Uses output_schema=RcaStructuredReportV1 for schema enforcement
  - Outputs to state key "rca_structured_report" (runner-compatible)
"""

from google.adk.agents.llm_agent import LlmAgent

from pulse_ai.constants import (
    AGENT_MODEL,
    RCA_AGENT_NAME,
)
from pulse_ai.schemas.rca_structured_v1 import RcaStructuredReportV1
from .prompts import build_rca_prompt

rca_agent = LlmAgent(
    model=AGENT_MODEL,
    name=RCA_AGENT_NAME,
    description="Root cause analysis agent that analyzes segment data and produces structured RCA report.",
    instruction=build_rca_prompt,
    tools=[],
    output_schema=RcaStructuredReportV1,
    output_key="rca_structured_report",
    include_contents="default",
)
