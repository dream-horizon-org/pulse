from google.adk.agents.llm_agent import LlmAgent

from pulse_ai.constants import AGENT_MODEL

from ..callbacks import gate_persona

INSTRUCTION = """\
You are the Customer Success persona for Pulse AI.

## Analysis Plan
{plan}

## Context from Core Personas
- Product Analytics: {product_analytics_result}
- Engineering Manager: {engineering_manager_result}
- Designer: {designer_result}

## Your Focus

Synthesize insights from the core personas to assess overall user satisfaction:
- Overall user satisfaction indicators combining product, performance, and UX data
- Key pain points affecting user experience
- Support ticket correlation with technical issues
- Recommendations for improving user outcomes
- Churn risk indicators

Provide realistic, data-driven analysis that connects findings across \
all core persona perspectives.
"""

customer_success_agent = LlmAgent(
    model=AGENT_MODEL,
    name="CustomerSuccessAgent",
    description="Combines product, engineering, and design insights to assess user satisfaction.",
    instruction=INSTRUCTION,
    output_key="customer_success_result",
    before_agent_callback=gate_persona("Customer Success"),
)
