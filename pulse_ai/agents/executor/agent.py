from google.adk.agents.llm_agent import LlmAgent

from pulse_ai.constants import AGENT_MODEL, EXECUTOR_AGENT_NAME

from ..callbacks import gate_on_clear_intent
from .prompts import EXECUTOR_INSTRUCTION

executor_agent = LlmAgent(
    model=AGENT_MODEL,
    name=EXECUTOR_AGENT_NAME,
    description="Iterates over selected personas and produces detailed per-persona analysis.",
    instruction=EXECUTOR_INSTRUCTION,
    output_key="execution_results",
    before_agent_callback=gate_on_clear_intent,
)
