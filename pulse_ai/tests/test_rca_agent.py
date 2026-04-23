"""Tests for RCA agent — Single-agent architecture.

Tests cover:
1. RCA agent wiring (model, output_key, output_schema, no tools, callable instruction)
2. RCA prompt content (hierarchy analysis, correlation, threshold flags, structured output)
3. Report agent genericization (callable prompt reads from EM state key)
"""

import pytest

from pulse_ai.agents.shared.schemas.rca_structured_v1 import RcaStructuredReportV1


# ──────────────────────────────────────────────────────────────
# Helper: mock ReadonlyContext with state dict
# ──────────────────────────────────────────────────────────────

class _MockState(dict):
    """Dict that also supports attribute-style access for ADK compatibility."""
    pass


class MockReadonlyContext:
    """Minimal stand-in for google.adk.agent_context.ReadonlyContext."""

    def __init__(self, state: dict = None):
        self.state = _MockState(state or {})


# ──────────────────────────────────────────────────────────────
# 1. RCA agent wiring
# ──────────────────────────────────────────────────────────────

class TestRcaAgentWiring:
    """Verify rca_agent is properly configured as an LlmAgent."""

    def test_rca_agent_exists(self):
        from pulse_ai.agents.rca import rca_agent
        assert rca_agent is not None

    def test_rca_agent_model(self):
        from pulse_ai.agents.rca import rca_agent
        from pulse_ai.agents.settings import AGENT_MODEL
        assert rca_agent.model == AGENT_MODEL

    def test_rca_agent_name(self):
        from pulse_ai.agents.rca import rca_agent
        assert rca_agent.name == "RcaAgent"

    def test_rca_agent_output_key(self):
        from pulse_ai.agents.rca import rca_agent
        assert rca_agent.output_key == "rca_structured_report"

    def test_rca_agent_output_schema(self):
        """RCA agent produces structured JSON via output_schema."""
        from pulse_ai.agents.rca import rca_agent
        assert rca_agent.output_schema == RcaStructuredReportV1

    def test_rca_agent_no_tools(self):
        """RCA agent is a pure reasoning agent — no tools needed."""
        from pulse_ai.agents.rca import rca_agent
        assert rca_agent.tools is None or len(rca_agent.tools) == 0

    def test_rca_agent_has_callable_instruction(self):
        from pulse_ai.agents.rca import rca_agent
        assert callable(rca_agent.instruction)

    def test_rca_agent_has_description(self):
        from pulse_ai.agents.rca import rca_agent
        assert rca_agent.description is not None
        assert "root cause" in rca_agent.description.lower()

    def test_rca_agent_include_contents(self):
        """RCA agent needs access to full conversation history for payload metrics."""
        from pulse_ai.agents.rca import rca_agent
        assert rca_agent.include_contents == "default"


# ──────────────────────────────────────────────────────────────
# 2. RCA prompt content
# ──────────────────────────────────────────────────────────────

class TestRcaPromptContent:
    """Verify the RCA system prompt contains key analytical instructions."""

    def _get_prompt(self) -> str:
        from pulse_ai.agents.rca.prompts import build_rca_prompt
        return build_rca_prompt(None)

    def test_prompt_mentions_root_cause(self):
        prompt = self._get_prompt()
        assert "root cause" in prompt.lower()

    def test_prompt_mentions_segment(self):
        prompt = self._get_prompt()
        assert "segment" in prompt.lower()

    def test_prompt_mentions_correlation(self):
        prompt = self._get_prompt()
        assert "correlat" in prompt.lower()

    def test_prompt_mentions_delta(self):
        prompt = self._get_prompt()
        assert "delta" in prompt.lower()

    def test_prompt_mentions_apdex(self):
        prompt = self._get_prompt()
        assert "apdex" in prompt.lower()

    def test_prompt_mentions_severity(self):
        prompt = self._get_prompt()
        assert "severity" in prompt.lower() or "critical" in prompt.lower()

    def test_prompt_mentions_metrics(self):
        """Prompt should reference the key input metrics."""
        prompt = self._get_prompt()
        metrics = ["error rate", "crash rate", "anr rate", "frozen frame"]
        found = sum(1 for m in metrics if m in prompt.lower())
        assert found >= 3, f"Expected at least 3 key metrics mentioned, found {found}"

    def test_prompt_mentions_output_schema(self):
        """Prompt should reference structured JSON output schema."""
        prompt = self._get_prompt()
        assert "version" in prompt.lower()
        assert "executive_summary" in prompt.lower()
        assert "segments" in prompt.lower()
        assert "recommendations" in prompt.lower()

    def test_prompt_mentions_affected_sessions(self):
        """Prompt should reference affected_sessions field."""
        prompt = self._get_prompt()
        assert "affected_sessions" in prompt.lower()

    def test_prompt_mentions_example_session_ids(self):
        """Prompt should reference exampleSessionIds from payload."""
        prompt = self._get_prompt()
        assert "examplesessionids" in prompt.lower() or "example_session_ids" in prompt.lower()


# ──────────────────────────────────────────────────────────────
# 3. Report agent genericization — callable prompt with multi-key state
# ──────────────────────────────────────────────────────────────

class TestReportAgentGeneric:
    """Verify the Report agent's instruction is now a callable that reads
    from whichever predecessor agent populated the shared state."""

    def test_report_agent_has_callable_instruction(self):
        from pulse_ai.agents.report import report_agent
        assert callable(report_agent.instruction)

    def test_report_prompt_reads_em_result(self):
        """When state has engineering_manager_result, the prompt includes it."""
        from pulse_ai.agents.report.prompts import build_report_prompt
        ctx = MockReadonlyContext({"engineering_manager_result": "EM analysis output here"})
        prompt = build_report_prompt(ctx)
        assert "EM analysis output here" in prompt

    def test_report_prompt_fallback_no_state(self):
        """When no analysis key is present, prompt shows fallback message."""
        from pulse_ai.agents.report.prompts import build_report_prompt
        ctx = MockReadonlyContext({})
        prompt = build_report_prompt(ctx)
        assert "no analysis" in prompt.lower() or "not available" in prompt.lower()

    def test_report_prompt_fallback_none_context(self):
        """When ctx is None (tests), prompt shows fallback."""
        from pulse_ai.agents.report.prompts import build_report_prompt
        prompt = build_report_prompt(None)
        assert "no analysis" in prompt.lower() or "not available" in prompt.lower()

    def test_report_prompt_still_has_visualization_instructions(self):
        """Generic prompt must still contain chart/table tool instructions."""
        from pulse_ai.agents.report.prompts import build_report_prompt
        prompt = build_report_prompt(None)
        assert "create_chart" in prompt
        assert "create_table" in prompt


# ──────────────────────────────────────────────────────────────
# 4. RCA prompt must NOT contain "Instructions for Report Agent"
# ──────────────────────────────────────────────────────────────

class TestRcaAnalyzerPromptClean:
    """Verify the RCA analyzer prompt has no embedded instructions
    addressed directly to the Report Agent — those belong in the
    Report Agent's own prompt, not in the analyzer's output."""

    def _get_rca_prompt(self) -> str:
        from pulse_ai.agents.rca.prompts import build_rca_prompt
        return build_rca_prompt(None)

    def test_rca_prompt_does_not_address_report_agent_directly(self):
        """The phrase 'Instructions for Report Agent' must not appear."""
        prompt = self._get_rca_prompt()
        assert "instructions for report agent" not in prompt.lower()

    def test_rca_prompt_does_not_have_do_not_include_block(self):
        """The 'DO NOT include:' block was a workaround — must be removed."""
        prompt = self._get_rca_prompt()
        assert "do not include:" not in prompt.lower()

    def test_rca_prompt_has_json_output_instructions(self):
        """The prompt must define structured JSON output format."""
        from pulse_ai.agents.rca.prompts import build_rca_prompt
        prompt = build_rca_prompt(None)
        assert "output schema" in prompt.lower() or "json" in prompt.lower()


# ──────────────────────────────────────────────────────────────
# 5. root_agent must be the EM pipeline (not rca agent)
# ──────────────────────────────────────────────────────────────

class TestRootAgentIsEmPipeline:
    """Verify root_agent is the EM SequentialAgent pipeline,
    not rca_agent. The rca_agent is run separately via rca_runner."""

    def test_root_agent_name_is_root_agent(self):
        from pulse_ai.agent import root_agent
        assert root_agent.name == "root_agent"

    def test_root_agent_first_sub_agent_is_em(self):
        from pulse_ai.agent import root_agent
        assert root_agent.sub_agents[0].name == "EMAgent"

    def test_root_agent_second_sub_agent_is_report(self):
        from pulse_ai.agent import root_agent
        assert root_agent.sub_agents[1].name == "ReportAgent"

    def test_root_agent_is_not_rca_agent(self):
        from pulse_ai.agent import root_agent
        assert root_agent.name != "RcaAgent"
