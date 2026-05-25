"""Interaction Research agent registration and harness checks."""

from pulse_ai.agents.interaction_research.agent import (
    DOCUMENTED_INTERACTION_RESEARCH_TOOLS,
    interaction_research_agent,
)
from pulse_ai.agents.interaction_research.tools import INTERACTION_RESEARCH_TOOL_NAMES
from pulse_ai.constants import INTERACTION_RESEARCH_AGENT_NAME
from pulse_ai.schemas.interaction_research_v1 import InteractionResearchV1


def test_agent_registered_with_bounded_tools():
    assert interaction_research_agent.name == INTERACTION_RESEARCH_AGENT_NAME
    tool_names = {t.__name__ for t in interaction_research_agent.tools}
    assert tool_names == set(INTERACTION_RESEARCH_TOOL_NAMES)
    assert tool_names == set(DOCUMENTED_INTERACTION_RESEARCH_TOOLS)


def test_agent_output_schema_is_interaction_research_v1():
    assert interaction_research_agent.output_schema is InteractionResearchV1
    assert interaction_research_agent.output_key == "interaction_research_v1"


def test_mandatory_tools_subset():
    mandatory = {
        "fetch_interaction_config",
        "fetch_interaction_metrics",
        "fetch_interaction_root_cause_segments",
    }
    assert mandatory.issubset(set(INTERACTION_RESEARCH_TOOL_NAMES))
