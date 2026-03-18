from __future__ import annotations

import asyncio
import json
import logging
from datetime import UTC, datetime
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import Request, urlopen

from pulse_ai.constants import BACKEND_REQUEST_TIMEOUT_SECONDS, PULSE_SERVER_BASE_URL
from pulse_ai.schemas import RootCausePayloadSchema

logger = logging.getLogger(__name__)

AUTHORIZATION_HEADER = "Authorization"
PROJECT_HEADER = "X-Project-ID"
SERVICE_KEY_HEADER = "X-Pulse-Service-Key"
DATE_QUERY_PARAM = "date"
ROOT_CAUSE_PATH_SUFFIX = "/v1/interactions/{interaction}/root-cause"
HTTP_TIMEOUT_GATEWAY = 504
HTTP_BAD_GATEWAY = 502


class BackendClientError(Exception):
    """Raised when pulse_ai cannot fetch root-cause payload from pulse-server."""

    def __init__(self, status_code: int, message: str) -> None:
        super().__init__(message)
        self.status_code = status_code
        self.message = message


def _resolve_effective_date(date_value: str | None) -> str:
    if date_value:
        return date_value
    return datetime.now(UTC).date().isoformat()


def _build_root_cause_url(interaction_name: str, date_value: str | None) -> str:
    encoded_interaction = quote(interaction_name, safe="")
    base_path = ROOT_CAUSE_PATH_SUFFIX.format(interaction=encoded_interaction)
    effective_date = _resolve_effective_date(date_value)
    return f"{PULSE_SERVER_BASE_URL}{base_path}?{DATE_QUERY_PARAM}={effective_date}"


def _build_headers(
    authorization: str | None,
    project_id: str | None,
    service_key: str | None = None,
) -> dict[str, str]:
    headers: dict[str, str] = {}
    if authorization:
        headers[AUTHORIZATION_HEADER] = authorization
    if project_id:
        headers[PROJECT_HEADER] = project_id
    if service_key:
        headers[SERVICE_KEY_HEADER] = service_key
    return headers


def _extract_root_cause_payload(response_json: dict) -> dict:
    data_value = response_json.get("data")
    if isinstance(data_value, dict):
        return data_value
    return response_json


def _perform_request(url: str, headers: dict[str, str]) -> dict:
    request = Request(url=url, headers=headers, method="GET")
    try:
        with urlopen(request, timeout=BACKEND_REQUEST_TIMEOUT_SECONDS) as response:
            response_body = response.read().decode("utf-8")
            return json.loads(response_body) if response_body else {}
    except HTTPError as error:
        error_status = error.code or HTTP_BAD_GATEWAY
        raise BackendClientError(error_status, "Failed to fetch root-cause data") from error
    except URLError as error:
        raise BackendClientError(HTTP_BAD_GATEWAY, "Pulse server unavailable") from error
    except TimeoutError as error:
        raise BackendClientError(HTTP_TIMEOUT_GATEWAY, "Root-cause fetch timed out") from error
    except json.JSONDecodeError as error:
        raise BackendClientError(HTTP_BAD_GATEWAY, "Invalid root-cause response payload") from error


async def fetch_root_cause_payload(
    interaction_name: str,
    date_value: str | None,
    authorization: str | None,
    project_id: str | None,
    service_key: str | None = None,
) -> RootCausePayloadSchema:
    """
    Fetches root-cause tabular payload from pulse-server.

    Timeout behavior:
    - Uses BACKEND_REQUEST_TIMEOUT_SECONDS.
    - Raises BackendClientError(504) on timeout.
    """
    request_url = _build_root_cause_url(interaction_name, date_value)
    request_headers = _build_headers(authorization, project_id, service_key)

    response_json = await asyncio.to_thread(_perform_request, request_url, request_headers)
    root_cause_json = _extract_root_cause_payload(response_json)
    return RootCausePayloadSchema.model_validate(root_cause_json)
