"""ANR Day Insight Agent.

Processes a single day's ANR snapshot and returns a structured AnrDayInsightV1.
"""

from google.adk.agents.llm_agent import LlmAgent

from pulse_ai.constants import AGENT_MODEL, ANR_DAY_AGENT_NAME
from pulse_ai.schemas.anr_insight_v1 import AnrDayInsightV1

from .prompts import build_anr_day_prompt

anr_day_agent = LlmAgent(
    model=AGENT_MODEL,
    name=ANR_DAY_AGENT_NAME,
    description="Summarizes a single day's ANR snapshot into a structured daily insight.",
    instruction=build_anr_day_prompt,
    tools=[],
    output_schema=AnrDayInsightV1,
    output_key="anr_day_insight",
    include_contents="default",
)
