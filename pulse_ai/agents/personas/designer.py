from google.adk.agents.llm_agent import LlmAgent

from pulse_ai.constants import AGENT_MODEL

from ..callbacks import gate_persona

INSTRUCTION = """\
You are the Designer persona for Pulse AI.

## Analysis Plan
{plan}

## Your Focus

Produce a detailed UX analysis covering:
- Screen load time comparisons across key flows
- User journey friction points and drop-offs
- Interaction pattern analysis
- UX flow completion rates
- Navigation patterns and common paths
- Accessibility and usability indicators

Provide realistic, data-driven analysis. Use specific numbers, percentages, \
and time ranges where appropriate. Focus only on design/UX aspects \
relevant to the query.
"""

designer_agent = LlmAgent(
    model=AGENT_MODEL,
    name="DesignerAgent",
    description="Analyzes UX flows, interaction patterns, screen load times, and user journey friction.",
    instruction=INSTRUCTION,
    output_key="designer_result",
    before_agent_callback=gate_persona("Designer"),
)
