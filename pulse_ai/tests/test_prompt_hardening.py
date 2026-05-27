"""Layer 2: prompt hardening tests — IDENTITY + IMPLEMENTATION PRIVACY on both agents.

IDENTITY and IMPLEMENTATION PRIVACY rules are injected by with_privacy() at agent
construction time, not inside the raw prompt functions. Tests that verify these
sections call the agent's instruction callable, not the raw prompt function.
"""
from pulse_ai.agents.em.agent import em_agent
from pulse_ai.agents.em.prompts import build_system_prompt
from pulse_ai.agents.report.agent import create_report_agent
from pulse_ai.agents.report.prompts import build_report_prompt


# ---------------------------------------------------------------------------
# EM Agent — full instruction (privacy wrapper applied)
# ---------------------------------------------------------------------------

class TestEmAgentIdentity:

    def test_em_instruction_contains_identity_section(self):
        prompt = em_agent.instruction(None)
        assert "IDENTITY:" in prompt

    def test_em_instruction_names_pulse_ai_as_product(self):
        prompt = em_agent.instruction(None)
        assert "Pulse AI" in prompt

    def test_em_instruction_forbids_internal_component_names(self):
        prompt = em_agent.instruction(None)
        assert "EMAgent" in prompt or "internal names" in prompt.lower()


class TestEmAgentImplementationPrivacy:

    def test_em_instruction_contains_implementation_privacy_section(self):
        prompt = em_agent.instruction(None)
        assert "IMPLEMENTATION PRIVACY:" in prompt

    def test_em_instruction_privacy_forbids_tool_function_names_in_output(self):
        prompt = em_agent.instruction(None)
        assert "tool function names" in prompt

    def test_em_instruction_privacy_provides_capability_framing(self):
        prompt = em_agent.instruction(None)
        assert "I can analyze interaction performance" in prompt


# ---------------------------------------------------------------------------
# Report Agent — full instruction (privacy wrapper applied)
# ---------------------------------------------------------------------------

class TestReportPromptPredecessorWording:

    def test_report_prompt_does_not_mention_engineering_manager(self):
        prompt = build_report_prompt(None)
        assert "predecessor agent (Engineering Manager)" not in prompt

    def test_report_prompt_uses_analysis_system_wording(self):
        prompt = build_report_prompt(None)
        assert "analysis system" in prompt


class TestReportAgentIdentity:

    def test_report_instruction_contains_identity_section(self):
        agent = create_report_agent()
        prompt = agent.instruction(None)
        assert "IDENTITY:" in prompt

    def test_report_instruction_names_pulse_ai(self):
        agent = create_report_agent()
        prompt = agent.instruction(None)
        assert "Pulse AI" in prompt

    def test_report_instruction_forbids_internal_agent_names(self):
        agent = create_report_agent()
        prompt = agent.instruction(None)
        assert "ReportAgent" in prompt or "internal names" in prompt.lower()


class TestReportAgentImplementationPrivacy:

    def test_report_instruction_contains_implementation_privacy_section(self):
        agent = create_report_agent()
        prompt = agent.instruction(None)
        assert "IMPLEMENTATION PRIVACY:" in prompt

    def test_report_instruction_privacy_forbids_revealing_viz_tool_names(self):
        agent = create_report_agent()
        prompt = agent.instruction(None)
        assert "function names" in prompt

    def test_report_prompt_preserves_create_chart_section_header(self):
        # ADK maps LLM tool calls to Python functions by exact name — must not change
        prompt = build_report_prompt(None)
        assert "### create_chart" in prompt

    def test_report_prompt_preserves_create_table_section_header(self):
        prompt = build_report_prompt(None)
        assert "### create_table" in prompt
