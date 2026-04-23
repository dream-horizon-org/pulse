"""Pulse AI — Root agent (SequentialAgent pipeline).

Orchestrates:  EM Agent (data analysis) → Report Agent (visualization)

Defined in ``agents`` so ADK Web can load ``root_agent`` when the app name is
``agents``. This module re-exports for ``pulse_ai.agent`` / server imports.
"""

from .agents import root_agent

__all__ = ["root_agent"]
