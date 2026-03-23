from google.adk.agents.llm_agent import LlmAgent
from google.adk.agents.sequential_agent import SequentialAgent

from pulse_ai.constants import (
    AGENT_MODEL,
    RCA_ANALYZER_AGENT_NAME,
    RCA_PIPELINE_AGENT_NAME,
    RCA_REPORT_AGENT_NAME,
)

from .prompts import RCA_ANALYZER_INSTRUCTION, RCA_REPORT_INSTRUCTION
from .tools import submit_rca_structured_report

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
    description="Emits versioned structured RCA report (v1).",
    instruction=RCA_REPORT_INSTRUCTION,
    tools=[submit_rca_structured_report],
)

rca_pipeline_agent = SequentialAgent(
    name=RCA_PIPELINE_AGENT_NAME,
    sub_agents=[rca_analyzer_agent, rca_report_agent],
    description="RCA report pipeline: analyze root-cause data then generate final report.",
)
