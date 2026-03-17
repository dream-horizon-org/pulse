from google.adk.agents.llm_agent import LlmAgent

from pulse_ai.constants import AGENT_MODEL

from ..callbacks import gate_persona

INSTRUCTION = """\
You are the Business Leaders persona for Pulse AI.

## Analysis Plan
{plan}

## Context from Core Personas
- Product Analytics: {product_analytics_result}
- Engineering Manager: {engineering_manager_result}
- Designer: {designer_result}

## Your Focus

Synthesize insights from the core personas for strategic decision-making:
- Strategic metrics combining all three core perspectives
- Risk areas requiring executive attention
- Growth opportunities identified from the data
- Resource allocation recommendations
- Competitive positioning implications

Provide realistic, data-driven analysis focused on executive-level \
insights and actionable recommendations.
"""

business_leaders_agent = LlmAgent(
    model=AGENT_MODEL,
    name="BusinessLeadersAgent",
    description="Combines product, engineering, and design insights for strategic decision-making.",
    instruction=INSTRUCTION,
    output_key="business_leaders_result",
    before_agent_callback=gate_persona("Business Leaders"),
)
