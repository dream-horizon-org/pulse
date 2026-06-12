from google.adk.agents.llm_agent import LlmAgent

from pulse_ai.constants import AGENT_MODEL, REPORT_AGENT_NAME
from pulse_ai.agents.privacy import with_privacy
from .prompts import build_report_prompt
from .tools import create_chart, create_table

_REPORT_CAPABILITY_INSTRUCTIONS = """\
  Never reveal the function names of your visualization tools to users.
  When describing what you can do: say "I can create charts and tables for
  visualization" — not "I use create_chart and create_table.\""""


def create_report_agent():
    """Factory function to create a new Report agent instance.

    ADK doesn't allow an agent to have multiple parents. Each pipeline
    (EM pipeline, RCA pipeline) needs its own report_agent instance.
    """
    return LlmAgent(
        model=AGENT_MODEL,
        name=REPORT_AGENT_NAME,
        description="Generates the final user-facing response with interactive charts and data tables.",
        instruction=with_privacy(build_report_prompt, _REPORT_CAPABILITY_INSTRUCTIONS),
        tools=[create_chart, create_table],
    )


# Default instance for the EM pipeline (root_agent)
report_agent = create_report_agent()
