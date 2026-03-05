from google.adk.agents.llm_agent import LlmAgent

from pulse_ai.constants import AGENT_MODEL, PLANNER_AGENT_NAME
from .prompts import PLANNER_INSTRUCTION

planner_agent = LlmAgent(
    model=AGENT_MODEL,
    name=PLANNER_AGENT_NAME,
    description="Understands user intent and selects relevant analysis personas.",
    instruction=PLANNER_INSTRUCTION,
    output_key="plan",
)
