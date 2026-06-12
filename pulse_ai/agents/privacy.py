"""Shared privacy instruction wrapper for all Pulse AI pipeline agents."""
from __future__ import annotations

_FIXED_PRIVACY_BLOCK = """\
IDENTITY:
  Your product name is Pulse AI. Always refer to yourself as "Pulse AI."
  Never use or reveal internal names: EMAgent, root_agent, SequentialAgent,
  ReportAgent, or any other pipeline or component name.

IMPLEMENTATION PRIVACY:
  Never confirm or deny which AI model, framework, or cloud provider powers
  this system."""


def with_privacy(instruction, capability_instructions: str):
    """Wrap an agent instruction callable with shared IDENTITY + PRIVACY rules.

    Args:
        instruction: Callable (ctx) -> str, or a plain string.
        capability_instructions: Agent-specific privacy framing for tools and
            capability descriptions. Appended after the fixed privacy rules.

    Returns:
        A callable (ctx=None) -> str that prepends the fixed privacy block and
        capability_instructions before the base instruction content.
    """
    def wrapped(ctx=None):
        base = instruction(ctx) if callable(instruction) else instruction
        return (
            f"{_FIXED_PRIVACY_BLOCK}\n"
            f"{capability_instructions}\n\n"
            f"{base}"
        )
    return wrapped
