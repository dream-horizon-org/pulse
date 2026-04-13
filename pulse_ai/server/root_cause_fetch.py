"""Fetch interaction root-cause tabular data from pulse-server (RCA callback path)."""

from __future__ import annotations

import json
from datetime import UTC, datetime
from urllib.parse import quote

from pulse_ai.client.pulse_client import PulseClient
from pulse_ai.constants import (
    HTTP_BAD_GATEWAY,
    HTTP_TIMEOUT_GATEWAY,
    ROOT_CAUSE_FETCH_DATE_QUERY_PARAM,
    ROOT_CAUSE_FETCH_PATH_TEMPLATE,
)
from pulse_ai.schemas import RootCausePayloadSchema


class RootCauseFetchError(Exception):
    """Raised when pulse_ai cannot load root-cause JSON from pulse-server."""

    def __init__(self, status_code: int, message: str) -> None:
        super().__init__(message)
        self.status_code = status_code
        self.message = message


def _resolve_effective_date(date_value: str | None) -> str:
    if date_value:
        return date_value
    return datetime.now(UTC).date().isoformat()


def _extract_root_cause_payload(response_json: dict) -> dict:
    data_value = response_json.get("data")
    if isinstance(data_value, dict):
        return data_value
    return response_json


def _fetch_error_from_pulse_client_dict(error_payload: dict) -> RootCauseFetchError:
    message = str(error_payload.get("message", ""))
    message_lower = message.lower()
    is_timeout = "timed out" in message_lower
    if is_timeout:
        return RootCauseFetchError(HTTP_TIMEOUT_GATEWAY, "Root-cause fetch timed out")
    return RootCauseFetchError(HTTP_BAD_GATEWAY, "Pulse server unavailable")


async def fetch_root_cause_payload(
    interaction_name: str,
    date_value: str | None,
    authorization: str,
    project_id: str,
) -> RootCausePayloadSchema:
    """
    Load root-cause tabular payload from pulse-server for an interaction.

    ``authorization`` must be a non-empty ``Authorization`` header value (e.g. ``Bearer <jwt>``).
    ``project_id`` is sent as ``X-Project-ID`` so pulse-server can authorize via OpenFGA.

    Uses :class:`~pulse_ai.client.pulse_client.PulseClient` (same stack as EM tools).
    """
    encoded_interaction = quote(interaction_name, safe="")
    path = ROOT_CAUSE_FETCH_PATH_TEMPLATE.format(interaction=encoded_interaction)
    effective_date = _resolve_effective_date(date_value)

    async with PulseClient(
        authorization_header=authorization,
        project_id=project_id,
    ) as client:
        raw = await client.request(
            "GET",
            path,
            params={ROOT_CAUSE_FETCH_DATE_QUERY_PARAM: effective_date},
        )

    if isinstance(raw, dict):
        raise _fetch_error_from_pulse_client_dict(raw)

    if raw.status_code != 200:
        raise RootCauseFetchError(
            raw.status_code or HTTP_BAD_GATEWAY,
            "Failed to fetch root-cause data",
        )

    try:
        response_json = raw.json()
    except json.JSONDecodeError as exc:
        raise RootCauseFetchError(
            HTTP_BAD_GATEWAY,
            "Invalid root-cause response payload",
        ) from exc

    root_cause_json = _extract_root_cause_payload(response_json)
    return RootCausePayloadSchema.model_validate(root_cause_json)
