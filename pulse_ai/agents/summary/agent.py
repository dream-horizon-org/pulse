from google.adk.agents.llm_agent import LlmAgent

from pulse_ai.constants import AGENT_MODEL, SUMMARY_AGENT_NAME

from ..callbacks import gate_summary
from .prompts import SUMMARY_INSTRUCTION

summary_agent = LlmAgent(
    model=AGENT_MODEL,
    name=SUMMARY_AGENT_NAME,
    description="Synthesizes cross-persona insights into a unified narrative.",
    instruction=SUMMARY_INSTRUCTION,
    output_key="summary",
    before_agent_callback=gate_summary,
)
