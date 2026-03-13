"""Tests for agent.py wiring — callable instruction, tool registration.

TDD RED: Tests for the updated agent.py with tools and system prompt.
"""

import pytest
from freezegun import freeze_time


class TestBuildSystemPrompt:
    """Callable instruction function that injects current UTC timestamp."""

    @freeze_time("2026-03-09T14:30:00Z")
    def test_prompt_contains_current_time(self):
        from pulse_ai.agent import build_system_prompt
        # ADK passes a ReadonlyContext as the first arg; we pass None for tests
        prompt = build_system_prompt(None)
        assert "2026-03-09T14:30:00" in prompt

    @freeze_time("2026-03-09T14:30:00Z")
    def test_prompt_contains_behavior_rules(self):
        from pulse_ai.agent import build_system_prompt
        prompt = build_system_prompt(None)
        assert "Pulse Engineering Manager" in prompt
        assert "LAST 24 HOURS" in prompt

    @freeze_time("2026-03-09T14:30:00Z")
    def test_prompt_contains_capabilities(self):
        from pulse_ai.agent import build_system_prompt
        prompt = build_system_prompt(None)
        assert "interaction" in prompt.lower()
        assert "alert" in prompt.lower()


class TestAgentWiring:
    """Verify root_agent is properly configured."""

    def test_root_agent_exists(self):
        from pulse_ai.agent import root_agent
        assert root_agent is not None

    def test_root_agent_name(self):
        from pulse_ai.agent import root_agent
        assert root_agent.name == "root_agent"

    def test_root_agent_has_tools(self):
        from pulse_ai.agent import root_agent
        # Should have 7 tools: 2 config + 4 analytics + 1 utility (calculate)
        assert root_agent.tools is not None
        assert len(root_agent.tools) == 7

    def test_root_agent_has_callable_instruction(self):
        from pulse_ai.agent import root_agent
        # instruction should be a callable (function), not a static string
        assert callable(root_agent.instruction)

    def test_root_agent_description(self):
        from pulse_ai.agent import root_agent
        assert "observability" in root_agent.description.lower() or "pulse" in root_agent.description.lower()
