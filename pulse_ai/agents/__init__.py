"""Pulse multi-agent package (EM, Report, RCA).

ADK Web prepends the project directory to ``sys.path`` and loads this package as
top-level ``agents``. Subpackages use **relative** imports so ``pulse_ai`` does
not need to be on ``sys.path``.

``root_agent`` is defined here so ADK can load the composite pipeline when the
selected app name is ``agents`` (the ``agents/`` directory under the project root).
"""

from __future__ import annotations

from google.adk.agents.sequential_agent import SequentialAgent

from .em.agent import em_agent
from .report.agent import report_agent
from .rca.agent import rca_agent

root_agent = SequentialAgent(
    name="root_agent",
    sub_agents=[em_agent, report_agent],
    description=(
        "Sequential pipeline: EM Agent (data analysis) → "
        "Report Agent (visualization with charts and tables)"
    ),
)
