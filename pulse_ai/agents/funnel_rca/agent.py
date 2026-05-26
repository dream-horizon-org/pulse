"""Funnel RCA: structured report from precomputed drop-off attribution causes."""

from google.adk.agents.llm_agent import LlmAgent

from pulse_ai.constants import AGENT_MODEL, FUNNEL_RCA_AGENT_NAME
from pulse_ai.schemas.funnel_rca_structured_v1 import FunnelRcaStructuredV1

from .prompts import build_funnel_rca_system_instruction

funnel_rca_agent = LlmAgent(
    model=AGENT_MODEL,
    name=FUNNEL_RCA_AGENT_NAME,
    description=(
        "Produces executive_summary, ranked OTel cause segments with lift metrics, "
        "and recommendations for funnel step drop-off RCA."
    ),
    instruction=build_funnel_rca_system_instruction,
    tools=[],
    output_schema=FunnelRcaStructuredV1,
    output_key="funnel_rca_structured",
)
