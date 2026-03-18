"""EM Agent — Engineering Manager persona for Pulse observability.

Uses a callable instruction (Mechanism B) to inject the current UTC
timestamp into the system prompt, and 7 MCP tools for data analysis.
"""

from dotenv import load_dotenv
from google.adk.agents.llm_agent import Agent

from pulse_ai.constants import AGENT_MODEL
from .prompts import build_system_prompt
from .tools import (
    query_interactions,
    query_alerts,
    query_interaction_health,
    query_interaction_metrics,
    query_interaction_sessions,
    breakdown_interaction,
    calculate,
)

load_dotenv()

em_agent = Agent(
    model=AGENT_MODEL,
    name='em_agent',
    description='Engineering Manager agent for Pulse mobile app observability',
    instruction=build_system_prompt,
    output_key='engineering_manager_result',
    tools=[
        query_interactions,
        query_alerts,
        query_interaction_health,
        query_interaction_metrics,
        query_interaction_sessions,
        breakdown_interaction,
        calculate,
    ],
)
