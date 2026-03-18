from google.adk.agents.llm_agent import LlmAgent
from google.adk.agents.sequential_agent import SequentialAgent

from pulse_ai.constants import (
    AGENT_MODEL,
    RCA_ANALYZER_AGENT_NAME,
    RCA_PIPELINE_AGENT_NAME,
    RCA_REPORT_AGENT_NAME,
)

from ..report.tools import create_chart, create_table
from .prompts import RCA_ANALYZER_INSTRUCTION, RCA_REPORT_INSTRUCTION

rca_analyzer_agent = LlmAgent(
    model=AGENT_MODEL,
    name=RCA_ANALYZER_AGENT_NAME,
    description="Analyzes root-cause tabular output and synthesizes RCA insights.",
    instruction=RCA_ANALYZER_INSTRUCTION,
    output_key="rca_insights",
)

rca_report_agent = LlmAgent(
    model=AGENT_MODEL,
    name=RCA_REPORT_AGENT_NAME,
    description="Generates markdown + charts/tables RCA report.",
    instruction=RCA_REPORT_INSTRUCTION,
    tools=[create_chart, create_table],
)

rca_pipeline_agent = SequentialAgent(
    name=RCA_PIPELINE_AGENT_NAME,
    sub_agents=[rca_analyzer_agent, rca_report_agent],
    description="RCA report pipeline: analyze root-cause data then generate final report.",
)
