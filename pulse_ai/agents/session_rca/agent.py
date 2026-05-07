"""Session RCA: single-step structured narrative (executive summary + segment insights + recommendations)."""

from google.adk.agents.llm_agent import LlmAgent

from pulse_ai.constants import AGENT_MODEL, SESSION_RCA_NARRATIVE_AGENT_NAME
from pulse_ai.schemas.session_rca_narrative_v1 import SessionRcaNarrativeV1

from .prompts import build_session_rca_system_instruction

session_rca_narrative_agent = LlmAgent(
    model=AGENT_MODEL,
    name=SESSION_RCA_NARRATIVE_AGENT_NAME,
    description=(
        "Produces executive_summary, segment_insights, and recommendations for session-level "
        "quality RCA from tabular RootCausePayload JSON."
    ),
    instruction=build_session_rca_system_instruction,
    tools=[],
    output_schema=SessionRcaNarrativeV1,
    output_key="session_rca_narrative",
)
