"""Minimal ADK app package for `adk eval` — RCA agent only.

The stock `adk eval` loader imports this folder as a package named `agent` and
expects `agent.agent.root_agent`. Production HTTP serving uses the full pipeline
(EM → RCA) via `pulse_ai.root_agent`. Evaluations target `rca_agent` in
isolation so scoring is not polluted by upstream EM output variation.
"""

from . import agent
from .agent import root_agent

__all__ = ["root_agent", "agent"]
