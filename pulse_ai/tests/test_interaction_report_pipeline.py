"""Sequential pipeline wiring for per-interaction health report (issue 04)."""

from google.adk.agents.sequential_agent import SequentialAgent

from pulse_ai.agents.interaction_research.agent import interaction_research_agent
from pulse_ai.agents.interaction_report.pipeline import interaction_report_pipeline
from pulse_ai.agents.interaction_report.schema_agent import interaction_report_schema_agent
from pulse_ai.constants import INTERACTION_REPORT_PIPELINE_NAME
from pulse_ai.schemas.interaction_report_v1 import InteractionReportV1
from pulse_ai.schemas.interaction_research_v1 import InteractionResearchV1Llm


def test_pipeline_is_sequential_agent_with_research_then_schema():
    assert isinstance(interaction_report_pipeline, SequentialAgent)
    assert interaction_report_pipeline.name == INTERACTION_REPORT_PIPELINE_NAME
    subs = interaction_report_pipeline.sub_agents
    assert len(subs) == 2
    assert subs[0] is interaction_research_agent
    assert subs[1] is interaction_report_schema_agent


def test_schema_agent_output_schema_is_interaction_report_v1():
    assert interaction_report_schema_agent.output_schema is InteractionReportV1
    assert interaction_report_schema_agent.output_key == "interaction_report_v1"
    assert interaction_report_schema_agent.tools == []


def test_research_agent_output_schema_is_interaction_research_v1():
    assert interaction_research_agent.output_schema is InteractionResearchV1Llm
    assert interaction_research_agent.output_key == "interaction_research_v1"
