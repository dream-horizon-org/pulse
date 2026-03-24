from google.adk.agents.llm_agent import LlmAgent

from pulse_ai.constants import AGENT_MODEL, REPORT_AGENT_NAME
from .prompts import REPORT_INSTRUCTION
from .tools import create_chart, create_table

report_agent = LlmAgent(
    model=AGENT_MODEL,
    name=REPORT_AGENT_NAME,
    description="Generates the final user-facing response with interactive charts and data tables.",
    instruction=REPORT_INSTRUCTION,
    tools=[create_chart, create_table],
)
