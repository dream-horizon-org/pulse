from google.adk.agents.parallel_agent import ParallelAgent
from google.adk.agents.sequential_agent import SequentialAgent

from .agents import (
    planner_agent,
    product_analytics_agent,
    engineering_manager_agent,
    designer_agent,
    customer_success_agent,
    business_leaders_agent,
    summary_agent,
    report_agent,
)
from .constants import (
    PIPELINE_AGENT_NAME,
    CORE_ANALYSIS_AGENT_NAME,
    DEPENDENT_ANALYSIS_AGENT_NAME,
)

core_analysis = ParallelAgent(
    name=CORE_ANALYSIS_AGENT_NAME,
    sub_agents=[
        product_analytics_agent,
        engineering_manager_agent,
        designer_agent,
    ],
    description="Runs core persona analyses in parallel.",
)

dependent_analysis = ParallelAgent(
    name=DEPENDENT_ANALYSIS_AGENT_NAME,
    sub_agents=[
        customer_success_agent,
        business_leaders_agent,
    ],
    description="Runs dependent persona analyses in parallel (after core results are available).",
)

root_agent = SequentialAgent(
    name=PIPELINE_AGENT_NAME,
    sub_agents=[
        planner_agent,
        core_analysis,
        dependent_analysis,
        summary_agent,
        report_agent,
    ],
    description=(
        "Sequential pipeline: Planner -> Core Personas (parallel) "
        "-> Dependent Personas (parallel) -> Summary -> Report"
    ),
)
