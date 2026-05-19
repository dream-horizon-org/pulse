"""ANR Merge Insight Agent.

Aggregates 30 daily ANR insights into a final AnrInsightReportV1.
"""

from google.adk.agents.llm_agent import LlmAgent

from pulse_ai.constants import AGENT_MODEL, ANR_MERGE_AGENT_NAME
from pulse_ai.schemas.anr_insight_v1 import AnrInsightReportV1

from .prompts import build_anr_merge_prompt

anr_merge_agent = LlmAgent(
    model=AGENT_MODEL,
    name=ANR_MERGE_AGENT_NAME,
    description=(
        "Aggregates daily ANR day-insights into a final date-range summary report, "
        "computing correct totals and trend direction."
    ),
    instruction=build_anr_merge_prompt,
    tools=[],
    output_schema=AnrInsightReportV1,
    output_key="anr_merge_report",
    include_contents="default",
)
