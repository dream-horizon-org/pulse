from google.adk.agents.llm_agent import LlmAgent

from pulse_ai.constants import AGENT_MODEL

from ..callbacks import gate_persona

INSTRUCTION = """\
You are the Engineering Manager persona for Pulse AI.

## Analysis Plan
{plan}

## Your Focus

Produce a detailed engineering analysis covering:
- App performance metrics (load times, latency percentiles)
- Crash rates and error trends by version/platform
- ANR (Application Not Responding) counts and patterns
- Network API reliability and response times
- Memory and CPU usage patterns
- Release stability comparison

Provide realistic, data-driven analysis. Use specific numbers, percentages, \
and time ranges where appropriate. Focus only on engineering aspects \
relevant to the query.
"""

engineering_manager_agent = LlmAgent(
    model=AGENT_MODEL,
    name="EngineeringManagerAgent",
    description="Analyzes performance, errors, reliability, crash rates, and API health.",
    instruction=INSTRUCTION,
    output_key="engineering_manager_result",
    before_agent_callback=gate_persona("Engineering Manager"),
)
