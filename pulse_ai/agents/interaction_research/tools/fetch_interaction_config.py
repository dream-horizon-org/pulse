"""Mandatory tool: interaction configuration (identity, marker events, thresholds)."""

from google.adk.tools import ToolContext

from pulse_ai.agents.em.tools.config.query_interactions import query_interactions


async def fetch_interaction_config(
    interaction_name: str,
    tool_context: ToolContext = None,
) -> dict:
    """Load interaction configuration for report block 1 (identity, marker events, thresholds).

    Args:
        interaction_name: Pulse interaction span name (e.g. PaymentGatewayHandshakeLatency).
    """
    return await query_interactions(
        scope="detail",
        interaction_name=interaction_name,
        tool_context=tool_context,
    )
