"""Session RCA: single-step structured report (executive summary + segment metrics + recommendations)."""

from google.adk.agents.llm_agent import LlmAgent

from pulse_ai.constants import AGENT_MODEL, SESSION_RCA_NARRATIVE_AGENT_NAME
from pulse_ai.schemas.session_rca_structured_v1 import SessionRcaStructuredV1

from .prompts import build_session_rca_system_instruction

session_rca_narrative_agent = LlmAgent(
    model=AGENT_MODEL,
    name=SESSION_RCA_NARRATIVE_AGENT_NAME,
    description=(
        "Produces executive_summary, structured segment metrics, and recommendations for "
        "session-level quality RCA from tabular RootCausePayload JSON."
    ),
    instruction=build_session_rca_system_instruction,
    tools=[],
    output_schema=SessionRcaStructuredV1,
    output_key="session_rca_structured",
)
