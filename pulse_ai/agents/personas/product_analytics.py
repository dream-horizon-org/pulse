from google.adk.agents.llm_agent import LlmAgent

from pulse_ai.constants import AGENT_MODEL

from ..callbacks import gate_persona

INSTRUCTION = """\
You are the Product Analytics persona for Pulse AI.

## Analysis Plan
{plan}

## Your Focus

Produce a detailed product analytics analysis covering:
- User engagement metrics and patterns
- Feature adoption and usage trends
- Funnel conversion rates and drop-off points
- Session frequency and retention indicators
- Cohort analysis where relevant

Provide realistic, data-driven analysis. Use specific numbers, percentages, \
and time ranges where appropriate. Focus only on product analytics aspects \
relevant to the query.
"""

product_analytics_agent = LlmAgent(
    model=AGENT_MODEL,
    name="ProductAnalyticsAgent",
    description="Analyzes usage patterns, funnels, feature adoption, and engagement metrics.",
    instruction=INSTRUCTION,
    output_key="product_analytics_result",
    before_agent_callback=gate_persona("Product Analytics"),
)
