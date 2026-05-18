"""Minimal ADK app package for `adk eval`.

The stock `adk eval` loader imports this folder's `__init__` as a synthetic
top-level module named `agent` and expects `agent.root_agent`. Production
HTTP serving continues to use `pulse_ai.root_agent` (EM → Report pipeline).

Evaluations target `em_agent` only so metrics are not dominated by the Report
agent's rewrite of the final answer.
"""

from . import agent
from .agent import root_agent

__all__ = ['root_agent', 'agent']
