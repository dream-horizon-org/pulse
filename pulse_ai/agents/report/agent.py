from google.adk.agents.llm_agent import LlmAgent

from ..settings import AGENT_MODEL, REPORT_AGENT_NAME
from .prompts import build_report_prompt
from .tools import create_chart, create_table


def create_report_agent():
    """Factory function to create a new Report agent instance.
    
    ADK doesn't allow an agent to have multiple parents. Each pipeline
    (EM pipeline, RCA pipeline) needs its own report_agent instance.
    """
    return LlmAgent(
        model=AGENT_MODEL,
        name=REPORT_AGENT_NAME,
        description="Generates the final user-facing response with interactive charts and data tables.",
        instruction=build_report_prompt,
        tools=[create_chart, create_table],
    )


# Default instance for the EM pipeline (root_agent)
report_agent = create_report_agent()
