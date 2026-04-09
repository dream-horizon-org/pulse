"""Tests for RCA agent and pipeline — TDD RED phase.

Tests cover:
1. RCA agent wiring (model, output_key, no tools, callable instruction)
2. RCA prompt content (hierarchy analysis, correlation, threshold flags)
3. RCA pipeline structure (SequentialAgent with rca_agent + report_agent)
4. Report agent genericization (callable prompt reads from multiple state keys)
"""

import pytest


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
        from pulse_ai.constants import AGENT_MODEL
        assert rca_agent.model == AGENT_MODEL

    def test_rca_agent_name(self):
        from pulse_ai.agents.rca import rca_agent
        assert rca_agent.name == "rca_agent"

    def test_rca_agent_output_key(self):
        from pulse_ai.agents.rca import rca_agent
        assert rca_agent.output_key == "rca_analysis_result"

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

    def test_prompt_mentions_segment_hierarchy(self):
        prompt = self._get_prompt()
        assert "segment" in prompt.lower() or "hierarchy" in prompt.lower()

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


# ──────────────────────────────────────────────────────────────
# 3. RCA pipeline structure
# ──────────────────────────────────────────────────────────────

class TestRcaPipeline:
    """Verify rca_pipeline is a SequentialAgent with rca_agent + report_agent."""

    def test_rca_pipeline_exists(self):
        from pulse_ai.agents.rca import rca_pipeline_agent
        assert rca_pipeline_agent is not None

    def test_rca_pipeline_is_sequential(self):
        from google.adk.agents.sequential_agent import SequentialAgent
        from pulse_ai.agents.rca import rca_pipeline_agent
        assert isinstance(rca_pipeline_agent, SequentialAgent)

    def test_rca_pipeline_name(self):
        from pulse_ai.agents.rca import rca_pipeline_agent
        assert rca_pipeline_agent.name == "rca_pipeline"

    def test_rca_pipeline_has_two_sub_agents(self):
        from pulse_ai.agents.rca import rca_pipeline_agent
        assert rca_pipeline_agent.sub_agents is not None
        assert len(rca_pipeline_agent.sub_agents) == 2

    def test_rca_pipeline_first_agent_is_rca(self):
        from pulse_ai.agents.rca import rca_pipeline_agent
        first = rca_pipeline_agent.sub_agents[0]
        assert first.name == "rca_agent"

    def test_rca_pipeline_second_agent_is_report(self):
        from pulse_ai.agents.rca import rca_pipeline_agent
        second = rca_pipeline_agent.sub_agents[1]
        assert second.name == "ReportAgent"

    def test_rca_pipeline_has_description(self):
        from pulse_ai.agents.rca import rca_pipeline_agent
        assert rca_pipeline_agent.description is not None
        assert "rca" in rca_pipeline_agent.description.lower()


# ──────────────────────────────────────────────────────────────
# 4. Report agent genericization — callable prompt with multi-key state
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

    def test_report_prompt_reads_rca_result(self):
        """When state has rca_analysis_result, the prompt includes it."""
        from pulse_ai.agents.report.prompts import build_report_prompt
        ctx = MockReadonlyContext({"rca_analysis_result": "RCA insight output here"})
        prompt = build_report_prompt(ctx)
        assert "RCA insight output here" in prompt

    def test_report_prompt_rca_takes_priority(self):
        """When both keys exist, rca_analysis_result takes priority
        (it was written last in its pipeline)."""
        from pulse_ai.agents.report.prompts import build_report_prompt
        ctx = MockReadonlyContext({
            "engineering_manager_result": "EM data",
            "rca_analysis_result": "RCA data",
        })
        prompt = build_report_prompt(ctx)
        assert "RCA data" in prompt

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
# 5. RCA-context-aware report prompt output format
# ──────────────────────────────────────────────────────────────

class TestRcaContextReportPrompt:
    """Verify that when rca_analysis_result is in state, the report prompt
    gives RCA-specific output guidance (concise 2-line summary + insights).
    """

    def _make_ctx(self, state: dict):
        return MockReadonlyContext(state)

    def test_rca_context_prompt_mentions_executive_summary(self):
        """When RCA result is in state, prompt must instruct the agent to
        surface the Executive Summary prominently."""
        from pulse_ai.agents.report.prompts import build_report_prompt
        ctx = self._make_ctx({"rca_analysis_result": "RCA output here"})
        prompt = build_report_prompt(ctx)
        assert "executive summary" in prompt.lower()

    def test_rca_context_prompt_instructs_concise_output(self):
        """When RCA result is in state, prompt must instruct the agent to
        keep its response concise / precise (not a verbose EM-style report)."""
        from pulse_ai.agents.report.prompts import build_report_prompt
        ctx = self._make_ctx({"rca_analysis_result": "RCA output here"})
        prompt = build_report_prompt(ctx)
        assert "concise" in prompt.lower() or "precise" in prompt.lower()

    def test_rca_context_prompt_asks_for_recommendations(self):
        """When RCA result is in state, prompt must ask for actionable
        recommendations / insights."""
        from pulse_ai.agents.report.prompts import build_report_prompt
        ctx = self._make_ctx({"rca_analysis_result": "RCA output here"})
        prompt = build_report_prompt(ctx)
        assert "recommendation" in prompt.lower() or "actionable" in prompt.lower()

    def test_rca_context_prompt_handles_no_anomalies(self):
        """When rca_analysis_result says no anomalies, prompt must instruct
        the agent to skip charts and confirm health."""
        from pulse_ai.agents.report.prompts import build_report_prompt
        ctx = self._make_ctx({"rca_analysis_result": "No significant anomalies detected."})
        prompt = build_report_prompt(ctx)
        # 'no significant anomalies' text is injected into the prompt
        assert "no significant anomalies" in prompt.lower()

    def test_em_context_prompt_does_not_mention_executive_summary(self):
        """When only EM result is in state (no RCA), prompt should NOT inject
        the RCA-specific executive summary instruction."""
        from pulse_ai.agents.report.prompts import build_report_prompt
        ctx = self._make_ctx({"engineering_manager_result": "EM analysis here"})
        prompt = build_report_prompt(ctx)
        # executive summary instruction is RCA-specific — should not bleed in
        assert "executive summary" not in prompt.lower()


# ──────────────────────────────────────────────────────────────
# 6. RCA analyzer prompt must NOT contain "Instructions for Report Agent"
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

    def test_rca_prompt_output_sections_are_only_rca_analysis_and_executive_summary(self):
        """The prompt's output format must define exactly 2 sections:
        'RCA Analysis' and 'Executive Summary'. No extra instructions block."""
        prompt = self._get_rca_prompt()
        # Both mandatory sections must still exist
        assert "rca analysis" in prompt.lower()
        assert "executive summary" in prompt.lower()


# ──────────────────────────────────────────────────────────────
# 7. root_agent must be the EM pipeline (not rca_pipeline)
# ──────────────────────────────────────────────────────────────

class TestRootAgentIsEmPipeline:
    """Verify root_agent is the EM SequentialAgent pipeline,
    not rca_pipeline. The rca_pipeline is run separately via rca_runner."""

    def test_root_agent_name_is_root_agent(self):
        from pulse_ai.agent import root_agent
        assert root_agent.name == "root_agent"

    def test_root_agent_first_sub_agent_is_em(self):
        from pulse_ai.agent import root_agent
        assert root_agent.sub_agents[0].name == "em_agent"

    def test_root_agent_second_sub_agent_is_report(self):
        from pulse_ai.agent import root_agent
        assert root_agent.sub_agents[1].name == "ReportAgent"

    def test_root_agent_is_not_rca_pipeline(self):
        from pulse_ai.agent import root_agent
        assert root_agent.name != "rca_pipeline"
