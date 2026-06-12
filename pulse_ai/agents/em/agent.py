"""EM Agent — Engineering Manager persona for Pulse observability.

Uses a callable instruction (Mechanism B) to inject the current UTC
timestamp into the system prompt, and 8 tools for data analysis.
"""

from dotenv import load_dotenv
from google.adk.agents.llm_agent import Agent

from pulse_ai.constants import AGENT_MODEL, EM_AGENT_NAME
from pulse_ai.output_guard import em_output_sanitize_callback
from pulse_ai.agents.privacy import with_privacy
from .prompts import build_system_prompt

_EM_CAPABILITY_INSTRUCTIONS = """\
  Never include tool function names, parameter names, API endpoints, database
  names, table names, column names, or infrastructure product names in your
  analysis output or in any response to the user.
  When asked about your tools or capabilities, describe them in user-facing
  terms only. Example: say "I can analyze interaction performance including
  Apdex, latency, error rates, and user categories" — not "I use
  query_interaction_health with parameters interaction_name and date_range."
  When asked what you can do, use this framing: "I can help you understand
  interaction performance, explore breakdowns by platform, device, OS, region
  and network, surface affected sessions, and review alert configurations.\""""
from .tools import (
    query_interactions,
    query_alerts,
    query_interaction_health,
    query_interaction_metrics,
    query_interaction_sessions,
    query_interaction_root_cause,
    breakdown_interaction,
    calculate,
)

load_dotenv()

em_agent = Agent(
    model=AGENT_MODEL,
    name=EM_AGENT_NAME,
    description='Engineering Manager agent for Pulse mobile app observability',
    instruction=with_privacy(build_system_prompt, _EM_CAPABILITY_INSTRUCTIONS),
    output_key='engineering_manager_result',
    after_agent_callback=em_output_sanitize_callback,
    tools=[
        query_interactions,
        query_alerts,
        query_interaction_health,
        query_interaction_metrics,
        query_interaction_sessions,
        query_interaction_root_cause,
        breakdown_interaction,
        calculate,
    ],
)
