"""Layer 2: prompt hardening tests — IDENTITY + IMPLEMENTATION PRIVACY on both agents."""
from pulse_ai.agents.em.prompts import build_system_prompt
from pulse_ai.agents.report.prompts import build_report_prompt


# ---------------------------------------------------------------------------
# EM Agent prompt
# ---------------------------------------------------------------------------

class TestEmPromptIdentity:

    def test_em_prompt_contains_identity_section(self):
        prompt = build_system_prompt(None)
        assert "IDENTITY:" in prompt

    def test_em_prompt_identity_names_pulse_ai_as_product(self):
        prompt = build_system_prompt(None)
        assert "Pulse AI" in prompt

    def test_em_prompt_identity_forbids_internal_component_names(self):
        prompt = build_system_prompt(None)
        # Must instruct the LLM not to reveal pipeline names
        assert "EMAgent" in prompt or "internal names" in prompt.lower()


class TestEmPromptImplementationPrivacy:

    def test_em_prompt_contains_implementation_privacy_section(self):
        prompt = build_system_prompt(None)
        assert "IMPLEMENTATION PRIVACY:" in prompt

    def test_em_prompt_privacy_forbids_tool_function_names_in_output(self):
        prompt = build_system_prompt(None)
        assert "tool function names" in prompt

    def test_em_prompt_privacy_provides_capability_framing(self):
        prompt = build_system_prompt(None)
        # Approved user-facing capability description must be present
        assert "I can analyze interaction performance" in prompt


# ---------------------------------------------------------------------------
# Report Agent prompt
# ---------------------------------------------------------------------------

class TestReportPromptPredecessorWording:

    def test_report_prompt_does_not_mention_engineering_manager(self):
        prompt = build_report_prompt(None)
        assert "predecessor agent (Engineering Manager)" not in prompt

    def test_report_prompt_uses_analysis_system_wording(self):
        prompt = build_report_prompt(None)
        assert "analysis system" in prompt


class TestReportPromptIdentity:

    def test_report_prompt_contains_identity_section(self):
        prompt = build_report_prompt(None)
        assert "IDENTITY:" in prompt

    def test_report_prompt_identity_names_pulse_ai(self):
        prompt = build_report_prompt(None)
        assert "Pulse AI" in prompt

    def test_report_prompt_identity_forbids_internal_agent_names(self):
        prompt = build_report_prompt(None)
        assert "ReportAgent" in prompt or "internal names" in prompt.lower()


class TestReportPromptImplementationPrivacy:

    def test_report_prompt_contains_implementation_privacy_section(self):
        prompt = build_report_prompt(None)
        assert "IMPLEMENTATION PRIVACY:" in prompt

    def test_report_prompt_privacy_forbids_revealing_viz_tool_names(self):
        prompt = build_report_prompt(None)
        assert "function names" in prompt

    def test_report_prompt_preserves_create_chart_section_header(self):
        # ADK maps LLM tool calls to Python functions by exact name — must not change
        prompt = build_report_prompt(None)
        assert "### create_chart" in prompt

    def test_report_prompt_preserves_create_table_section_header(self):
        prompt = build_report_prompt(None)
        assert "### create_table" in prompt
