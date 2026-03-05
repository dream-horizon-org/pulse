from google.adk.agents.sequential_agent import SequentialAgent

from .agents import planner_agent, executor_agent, summary_agent, report_agent
from .constants import PIPELINE_AGENT_NAME

root_agent = SequentialAgent(
    name=PIPELINE_AGENT_NAME,
    sub_agents=[planner_agent, executor_agent, summary_agent, report_agent],
    description="Sequential pipeline: Planner -> Executor -> Summary -> Report",
)
