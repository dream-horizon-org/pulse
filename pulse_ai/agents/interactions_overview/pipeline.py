from google.adk.agents import SequentialAgent

from pulse_ai.agents.em.agent import _build_em_agent
from pulse_ai.agents.summary import summary_agent

# Fresh EM agent instance — each pipeline wiring requires its own instance
# because ADK enforces a single-parent constraint on agent objects.
_em_agent_for_overview = _build_em_agent()

interactions_overview_pipeline = SequentialAgent(
    name="interactions_overview_pipeline",
    sub_agents=[_em_agent_for_overview, summary_agent],
)
