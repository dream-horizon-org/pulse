"""GET-only tabular root-cause fetch for per-interaction health reports (phase 1).

Does not use the legacy async ``POST /v1/ai/rca/report`` LLM job pipeline.
"""

from __future__ import annotations

from datetime import UTC, datetime
from pulse_ai.client.pulse_client import PulseClient
from pulse_ai.root_cause_payload_fetch import (
    RootCauseFetchError,
    _fetch_root_cause_tabular_direct,
)
from pulse_ai.schemas import RootCausePayloadSchema


def _resolve_effective_date(date_value: str | None) -> str:
    if date_value:
        return date_value.strip()
    return datetime.now(UTC).date().isoformat()


async def fetch_interaction_root_cause_segments(
    interaction_name: str,
    date_value: str | None,
    authorization: str,
    project_id: str,
) -> RootCausePayloadSchema:
    """Load tabular RCA via ``GET /v1/interactions/{name}/root-cause`` only.

    Same segment shape as the Interaction dashboard RCA tab (label, volume, poor %, deltas).
    """
    name = (interaction_name or "").strip()
    if not name:
        raise RootCauseFetchError(400, "interaction_name is required")

    effective_date = _resolve_effective_date(date_value)

    async with PulseClient(
        authorization_header=authorization,
        project_id=project_id,
    ) as client:
        return await _fetch_root_cause_tabular_direct(client, name, effective_date)
