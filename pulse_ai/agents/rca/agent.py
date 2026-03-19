"""RCA (Root Cause Analysis) agent and pipeline.

The RCA agent analyzes hierarchical segment data to identify correlations,
anomalies, and root causes. It outputs explainable insights that are then
formatted by the Report agent into charts and tables.
"""

from google.adk.agents.llm_agent import LlmAgent
from google.adk.agents.sequential_agent import SequentialAgent

from pulse_ai.constants import AGENT_MODEL, RCA_ANALYZER_AGENT_NAME, RCA_PIPELINE_AGENT_NAME, RCA_REPORT_AGENT_NAME
from pulse_ai.agents.report.prompts import build_report_prompt
from pulse_ai.agents.report.tools import create_chart, create_table
from pulse_ai.agents.rca.tools import submit_rca_structured_report
from .prompts import build_rca_prompt

rca_agent = LlmAgent(
    model=AGENT_MODEL,
    name=RCA_ANALYZER_AGENT_NAME,
    description="Root Cause Analysis agent that identifies correlations and anomalies in hierarchical segment data.",
    instruction=build_rca_prompt,
    tools=[],  # Pure reasoning agent — no tools needed
    output_key="rca_analysis_result",
)

# Separate instance from the EM pipeline's report_agent — ADK doesn't allow an agent to have multiple parents
rca_report_agent = LlmAgent(
    model=AGENT_MODEL,
    name=RCA_REPORT_AGENT_NAME,
    description="Generates the final user-facing response with interactive charts and data tables.",
    instruction=build_report_prompt,
    tools=[create_chart, create_table, submit_rca_structured_report],
)

rca_pipeline_agent = SequentialAgent(
    name=RCA_PIPELINE_AGENT_NAME,
    sub_agents=[rca_agent, rca_report_agent],
    description=(
        "Sequential pipeline: RCA Agent (root cause analysis) → "
        "Report Agent (visualization with charts and tables)"
    ),
)
