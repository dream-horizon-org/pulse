"""Screen RCA: single-step structured narrative (executive summary + recommendations)."""

from google.adk.agents.llm_agent import LlmAgent

from pulse_ai.constants import AGENT_MODEL, SCREEN_RCA_NARRATIVE_AGENT_NAME
from pulse_ai.schemas.screen_rca_narrative_v1 import ScreenRcaNarrativeV1

from .prompts import build_screen_rca_system_instruction

screen_rca_narrative_agent = LlmAgent(
    model=AGENT_MODEL,
    name=SCREEN_RCA_NARRATIVE_AGENT_NAME,
    description=(
        "Produces executive_summary and recommendations for screen-level frustration RCA "
        "from tabular RootCausePayload JSON."
    ),
    instruction=build_screen_rca_system_instruction,
    tools=[],
    output_schema=ScreenRcaNarrativeV1,
    output_key="screen_rca_narrative",
)
