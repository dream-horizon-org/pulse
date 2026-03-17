from google.adk.agents.llm_agent import LlmAgent

from pulse_ai.constants import AGENT_MODEL, PLANNER_AGENT_NAME

from ..callbacks import set_routing_flags
from ..schemas import PlanOutput
from .prompts import PLANNER_INSTRUCTION

planner_agent = LlmAgent(
    model=AGENT_MODEL,
    name=PLANNER_AGENT_NAME,
    description="Understands user intent and selects relevant analysis personas.",
    instruction=PLANNER_INSTRUCTION,
    output_key="plan",
    output_schema=PlanOutput,
    after_agent_callback=set_routing_flags,
)
