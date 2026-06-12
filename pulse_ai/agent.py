"""Pulse AI — Root agent (SequentialAgent pipeline).

Orchestrates:  EM Agent (data analysis) → Report Agent (visualization)
"""

from google.adk.agents.sequential_agent import SequentialAgent

from .agents import em_agent, report_agent

root_agent = SequentialAgent(
    name='root_agent',
    sub_agents=[em_agent, report_agent],
    description=(
        'Sequential pipeline: EM Agent (data analysis) → '
        'Report Agent (visualization with charts and tables)'
    ),
)
