"""Screen RCA v2: multi-problem structured report (executive summary + recommendations)."""

from google.adk.agents.llm_agent import LlmAgent

from pulse_ai.constants import AGENT_MODEL, SCREEN_RCA_V2_AGENT_NAME
from pulse_ai.schemas.screen_rca_structured_v2 import ScreenRcaStructuredV2

from .prompts import build_screen_rca_v2_system_instruction

screen_rca_v2_agent = LlmAgent(
    model=AGENT_MODEL,
    name=SCREEN_RCA_V2_AGENT_NAME,
    description=(
        "Produces executive_summary and recommendations for screen-level multi-problem RCA. "
        "Problems list and evidences are pre-computed and passed through unchanged."
    ),
    instruction=build_screen_rca_v2_system_instruction,
    tools=[],
    output_schema=ScreenRcaStructuredV2,
    output_key="screen_rca_structured_v2",
)
