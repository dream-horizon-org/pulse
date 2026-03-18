"""Tests for report agent integration — TDD RED phase.

Tests cover:
1. Report agent module structure (agents/report/ folder)
2. Report tools (create_chart, create_table)
3. Report utils (normalize_chart_data, normalize_table_data)
4. Root agent restructure (SequentialAgent with em_agent + report_agent)
5. Constants additions (AGENT_MODEL, REPORT_AGENT_NAME)
"""

import json

import pytest


# ──────────────────────────────────────────────────────────────
# 1. Constants — new exports
# ──────────────────────────────────────────────────────────────

class TestConstants:
    """Verify AGENT_MODEL and REPORT_AGENT_NAME are available."""

    def test_agent_model_exists(self):
        from pulse_ai.constants import AGENT_MODEL
        assert AGENT_MODEL is not None
        assert isinstance(AGENT_MODEL, str)

    def test_agent_model_default_value(self):
        from pulse_ai.constants import AGENT_MODEL, DEFAULT_MODEL
        # When AGENT_MODEL env var is not set, should equal DEFAULT_MODEL
        assert AGENT_MODEL == DEFAULT_MODEL

    def test_report_agent_name_exists(self):
        from pulse_ai.constants import REPORT_AGENT_NAME
        assert REPORT_AGENT_NAME == "ReportAgent"


# ──────────────────────────────────────────────────────────────
# 2. Report utils — chart & table normalization
# ──────────────────────────────────────────────────────────────

class TestReportUtils:
    """Test normalize_chart_data and normalize_table_data."""

    def test_normalize_chart_line(self):
        from pulse_ai.agents.report.utils import normalize_chart_data
        data = {
            "xAxis": {"type": "category", "data": ["Mon", "Tue"]},
            "yAxis": {"type": "value"},
            "series": [{"name": "Errors", "data": [10, 20]}],
        }
        result = normalize_chart_data("line", data)
        assert "series" in result
        assert result["series"][0]["type"] == "line"

    def test_normalize_chart_pie_removes_axes(self):
        from pulse_ai.agents.report.utils import normalize_chart_data
        data = {
            "xAxis": {"type": "category"},
            "series": [{"type": "pie", "data": [{"name": "A", "value": 10}]}],
        }
        result = normalize_chart_data("pie", data)
        assert "xAxis" not in result
        assert "yAxis" not in result

    def test_normalize_chart_invalid_data(self):
        from pulse_ai.agents.report.utils import normalize_chart_data
        result = normalize_chart_data("line", "not a dict")
        assert result == {}

    def test_normalize_table_data_basic(self):
        from pulse_ai.agents.report.utils import normalize_table_data
        columns = [
            {"key": "name", "label": "Name", "type": "string"},
            {"key": "value", "label": "Value", "type": "number"},
        ]
        rows = [{"name": "A", "value": 10}, {"name": "B", "value": 20}]
        norm_cols, norm_rows = normalize_table_data(columns, rows)
        assert len(norm_cols) == 2
        assert len(norm_rows) == 2
        assert norm_rows[0]["value"] == 10

    def test_normalize_table_data_coerces_string_numbers(self):
        from pulse_ai.agents.report.utils import normalize_table_data
        columns = [{"key": "val", "label": "Val", "type": "number"}]
        rows = [{"val": "42"}]
        _, norm_rows = normalize_table_data(columns, rows)
        assert norm_rows[0]["val"] == 42

    def test_normalize_table_empty_input(self):
        from pulse_ai.agents.report.utils import normalize_table_data
        cols, rows = normalize_table_data([], [])
        assert cols == []
        assert rows == []

    def test_coerce_number(self):
        from pulse_ai.agents.report.utils import coerce_number
        assert coerce_number(42) == 42
        assert coerce_number("3.14") == 3.14
        assert coerce_number("hello") == "hello"


# ──────────────────────────────────────────────────────────────
# 3. Report tools — create_chart, create_table
# ──────────────────────────────────────────────────────────────

class TestCreateChart:
    """Test the create_chart tool function."""

    @pytest.mark.asyncio
    async def test_create_line_chart(self):
        from pulse_ai.agents.report.tools import create_chart
        data_json = json.dumps({
            "xAxis": {"type": "category", "data": ["Mon", "Tue"]},
            "yAxis": {"type": "value"},
            "series": [{"name": "Errors", "data": [10, 20]}],
        })
        result = await create_chart(
            chart_type="line",
            title="Error Trend",
            data=data_json,
            description="Weekly error trend",
        )
        assert result["success"] is True
        assert result["chart"]["type"] == "line"
        assert result["chart"]["title"] == "Error Trend"

    @pytest.mark.asyncio
    async def test_create_pie_chart(self):
        from pulse_ai.agents.report.tools import create_chart
        data_json = json.dumps({
            "series": [{"type": "pie", "data": [
                {"name": "Android", "value": 60},
                {"name": "iOS", "value": 40},
            ]}],
        })
        result = await create_chart(
            chart_type="pie",
            title="Platform Split",
            data=data_json,
        )
        assert result["success"] is True
        assert result["chart"]["type"] == "pie"

    @pytest.mark.asyncio
    async def test_invalid_chart_type_defaults_to_line(self):
        from pulse_ai.agents.report.tools import create_chart
        result = await create_chart(
            chart_type="invalid",
            title="Test",
            data="{}",
        )
        assert result["chart"]["type"] == "line"

    @pytest.mark.asyncio
    async def test_invalid_json_data(self):
        from pulse_ai.agents.report.tools import create_chart
        result = await create_chart(
            chart_type="bar",
            title="Test",
            data="not valid json",
        )
        assert result["success"] is True
        # Should still return a chart config with normalized defaults
        assert "series" in result["chart"]["data"]


class TestCreateTable:
    """Test the create_table tool function."""

    @pytest.mark.asyncio
    async def test_create_basic_table(self):
        from pulse_ai.agents.report.tools import create_table
        columns = json.dumps([
            {"key": "interaction", "label": "Interaction", "type": "string"},
            {"key": "apdex", "label": "Apdex", "type": "number"},
        ])
        rows = json.dumps([
            {"interaction": "ProfileLoad", "apdex": 0.92},
            {"interaction": "ContestJoin", "apdex": 0.80},
        ])
        result = await create_table(
            title="Interaction Health",
            columns=columns,
            rows=rows,
        )
        assert result["success"] is True
        assert result["table"]["title"] == "Interaction Health"
        assert len(result["table"]["columns"]) == 2
        assert len(result["table"]["rows"]) == 2

    @pytest.mark.asyncio
    async def test_table_with_invalid_json(self):
        from pulse_ai.agents.report.tools import create_table
        result = await create_table(
            title="Test",
            columns="not json",
            rows="not json",
        )
        assert result["success"] is True
        assert result["table"]["columns"] == []
        assert result["table"]["rows"] == []


# ──────────────────────────────────────────────────────────────
# 4. Report agent wiring
# ──────────────────────────────────────────────────────────────

class TestReportAgentWiring:
    """Verify report_agent is properly configured."""

    def test_report_agent_exists(self):
        from pulse_ai.agents.report import report_agent
        assert report_agent is not None

    def test_report_agent_name(self):
        from pulse_ai.agents.report import report_agent
        assert report_agent.name == "ReportAgent"

    def test_report_agent_has_tools(self):
        from pulse_ai.agents.report import report_agent
        # Should have 2 tools: create_chart, create_table
        assert report_agent.tools is not None
        assert len(report_agent.tools) == 2

    def test_report_agent_has_instruction(self):
        from pulse_ai.agents.report import report_agent
        assert report_agent.instruction is not None


# ──────────────────────────────────────────────────────────────
# 5. Root agent restructure — SequentialAgent
# ──────────────────────────────────────────────────────────────

class TestRootAgentRestructure:
    """Verify root_agent is now a SequentialAgent with em_agent + report_agent."""

    def test_root_agent_exists(self):
        from pulse_ai.agent import root_agent
        assert root_agent is not None

    def test_root_agent_is_sequential(self):
        from google.adk.agents.sequential_agent import SequentialAgent
        from pulse_ai.agent import root_agent
        assert isinstance(root_agent, SequentialAgent)

    def test_root_agent_name(self):
        from pulse_ai.agent import root_agent
        assert root_agent.name == "root_agent"

    def test_root_agent_has_two_sub_agents(self):
        from pulse_ai.agent import root_agent
        assert root_agent.sub_agents is not None
        assert len(root_agent.sub_agents) == 2

    def test_first_sub_agent_is_em(self):
        from pulse_ai.agent import root_agent
        em = root_agent.sub_agents[0]
        assert em.name == "em_agent"

    def test_second_sub_agent_is_report(self):
        from pulse_ai.agent import root_agent
        report = root_agent.sub_agents[1]
        assert report.name == "ReportAgent"

    def test_em_agent_has_tools(self):
        from pulse_ai.agent import root_agent
        em = root_agent.sub_agents[0]
        # EM agent should have 7 tools: 2 config + 4 analytics + 1 utility
        assert em.tools is not None
        assert len(em.tools) == 7

    def test_em_agent_has_callable_instruction(self):
        from pulse_ai.agent import root_agent
        em = root_agent.sub_agents[0]
        assert callable(em.instruction)

    def test_em_agent_has_output_key(self):
        from pulse_ai.agent import root_agent
        em = root_agent.sub_agents[0]
        assert em.output_key == "engineering_manager_result"

    def test_report_agent_has_tools(self):
        from pulse_ai.agent import root_agent
        report = root_agent.sub_agents[1]
        # Report agent should have 2 tools: create_chart, create_table
        assert report.tools is not None
        assert len(report.tools) == 2
