"""Fetch interaction root-cause tabular data from pulse-server (RCA + EM tool)."""

from __future__ import annotations

import asyncio
import json
import logging
import time
from datetime import UTC, datetime
from urllib.parse import quote, urlencode

import httpx

logger = logging.getLogger(__name__)

from pulse_ai.client.pulse_client import PulseClient
from pulse_ai.constants import (
    HTTP_BAD_GATEWAY,
    HTTP_TIMEOUT_GATEWAY,
    RCA_ASYNC_RCA_TYPE_INTERACTION,
    RCA_JOB_GET_PATH_TEMPLATE,
    RCA_JOB_POLL_INTERVAL_SEC,
    RCA_PIPELINE_TIMEOUT_SECONDS,
    RCA_REPORT_PEEK_PATH_PREFIX,
    RCA_REPORT_POST_PATH,
)
from pulse_ai.schemas import RootCausePayloadSchema

HTTP_ACCEPTED = 202


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


def _unwrap_pulse_server_body(top: dict) -> dict:
    """Unwrap JAX-RS ``Response`` JSON ``{ "data": T }`` (and optional nested ``data``)."""
    cur: dict = top
    for _ in range(3):
        inner = cur.get("data")
        if isinstance(inner, dict):
            nested = inner.get("data")
            if isinstance(nested, dict) and (
                "jobId" in nested
                or "status" in nested
                or "report" in nested
                or "baseline" in nested
            ):
                cur = nested
                continue
            cur = inner
            continue
        break
    return cur


def _report_dict_candidates(unwrapped: dict) -> list[dict]:
    """Collect dicts that may hold ``rootCausePayload`` (job/peek/cache shapes)."""
    out: list[dict] = []
    report = unwrapped.get("report")
    if isinstance(report, dict):
        out.append(report)
        nested = report.get("report")
        if isinstance(nested, dict):
            out.append(nested)
    out.append(unwrapped)
    return out


def _extract_root_cause_payload_from_rca_report(unwrapped: dict) -> dict | None:
    """Read tabular ``rootCausePayload`` embedded in a completed async RCA report."""
    for obj in _report_dict_candidates(unwrapped):
        rcp = obj.get("rootCausePayload")
        if isinstance(rcp, dict) and rcp:
            return rcp
    return None


async def _fetch_root_cause_tabular_direct(
    client: PulseClient,
    interaction_name: str,
    effective_date: str,
) -> RootCausePayloadSchema:
    """GET ``/v1/interactions/{name}/root-cause`` — ClickHouse-backed tabular JSON.

    Used when async RCA cache (peek/job) returns COMPLETED but has no embedded
    ``rootCausePayload`` (legacy cache rows, or enrichment skipped). Matches the
    fallback described on ``InteractionController#getRootCause``.
    """
    path = (
        f"/v1/interactions/{quote(interaction_name, safe='')}/root-cause"
        f"?date={quote(effective_date, safe='')}"
    )
    raw = await client.request("GET", path)
    if isinstance(raw, dict):
        raise _fetch_error_from_pulse_client_dict(raw)
    if raw.status_code != 200:
        raise _root_cause_fetch_error_from_http_response(raw)
    try:
        payload = raw.json()
    except json.JSONDecodeError as exc:
        raise RootCauseFetchError(
            HTTP_BAD_GATEWAY, "Invalid JSON from interactions root-cause endpoint"
        ) from exc
    unwrapped = _unwrap_pulse_server_body(payload)
    try:
        return RootCausePayloadSchema.model_validate(unwrapped)
    except Exception as exc:
        raise RootCauseFetchError(
            HTTP_BAD_GATEWAY,
            f"Could not parse interactions root-cause tabular payload: {exc}",
        ) from exc


async def _tabular_from_completed_rca_response(
    client: PulseClient,
    unwrapped: dict,
    interaction_name: str,
    effective_date: str,
) -> RootCausePayloadSchema:
    """Prefer embedded ``rootCausePayload`` on the async RCA body; else direct GET."""
    tabular = _extract_root_cause_payload_from_rca_report(unwrapped)
    if tabular is not None:
        try:
            return RootCausePayloadSchema.model_validate(tabular)
        except Exception:
            logger.warning(
                "Embedded rootCausePayload failed validation; falling back to GET /root-cause",
                exc_info=True,
            )
    return await _fetch_root_cause_tabular_direct(client, interaction_name, effective_date)


def _fetch_error_from_pulse_client_dict(error_payload: dict) -> RootCauseFetchError:
    message = str(error_payload.get("message", ""))
    message_lower = message.lower()
    is_timeout = "timed out" in message_lower
    if is_timeout:
        return RootCauseFetchError(HTTP_TIMEOUT_GATEWAY, "Root-cause fetch timed out")
    return RootCauseFetchError(HTTP_BAD_GATEWAY, "Pulse server unavailable")


def _root_cause_fetch_error_from_http_response(raw: httpx.Response) -> RootCauseFetchError:
    code = raw.status_code or HTTP_BAD_GATEWAY
    try:
        body = raw.json()
    except json.JSONDecodeError:
        return RootCauseFetchError(code, "Failed to fetch root-cause data")
    if isinstance(body, dict):
        unwrapped = _unwrap_pulse_server_body(body)
        err = unwrapped.get("error")
        if isinstance(err, dict) and err.get("message"):
            return RootCauseFetchError(code, str(err.get("message")))
        msg = unwrapped.get("message")
        if msg:
            return RootCauseFetchError(code, str(msg))
    return RootCauseFetchError(code, "Failed to fetch root-cause data")


def _normalize_job_status(status: object) -> str:
    if status is None:
        return ""
    return str(status).strip().upper()


async def _peek_rca_report_status(
    client: PulseClient,
    interaction_name: str,
    effective_date: str,
) -> httpx.Response | dict:
    query = urlencode(
        {
            "rcaType": RCA_ASYNC_RCA_TYPE_INTERACTION,
            "entityKey": interaction_name,
            "date": effective_date,
        }
    )
    path = f"{RCA_REPORT_PEEK_PATH_PREFIX}?{query}"
    return await client.request("GET", path)


async def _post_rca_report(
    client: PulseClient,
    interaction_name: str,
    effective_date: str,
) -> httpx.Response | dict:
    body = {
        "rcaType": RCA_ASYNC_RCA_TYPE_INTERACTION,
        "entityKey": interaction_name,
        "date": effective_date,
    }
    return await client.request("POST", RCA_REPORT_POST_PATH, json=body)


async def _get_rca_job(client: PulseClient, job_id: str) -> httpx.Response | dict:
    encoded = quote(job_id, safe="")
    path = RCA_JOB_GET_PATH_TEMPLATE.format(job_id=encoded)
    return await client.request("GET", path)


async def _wait_for_rca_job_terminal(
    client: PulseClient,
    job_id: str,
) -> dict:
    deadline = time.monotonic() + float(RCA_PIPELINE_TIMEOUT_SECONDS)
    last_payload: dict = {}
    while time.monotonic() < deadline:
        raw = await _get_rca_job(client, job_id)
        if isinstance(raw, dict):
            raise _fetch_error_from_pulse_client_dict(raw)
        if raw.status_code != 200:
            raise _root_cause_fetch_error_from_http_response(raw)
        last_payload = _unwrap_pulse_server_body(raw.json())
        status = _normalize_job_status(last_payload.get("status"))
        if status == "COMPLETED":
            return last_payload
        if status == "FAILED":
            msg = str(last_payload.get("errorMessage") or "RCA report job failed")
            raise RootCauseFetchError(HTTP_BAD_GATEWAY, msg)
        if status not in ("PENDING", "PROCESSING", ""):
            raise RootCauseFetchError(
                HTTP_BAD_GATEWAY,
                f"Unexpected RCA job status: {last_payload.get('status')!r}",
            )
        await asyncio.sleep(RCA_JOB_POLL_INTERVAL_SEC)

    raise RootCauseFetchError(
        HTTP_TIMEOUT_GATEWAY,
        "RCA job polling timed out before completion",
    )


async def _orchestrate_rca_job_then_tabular(
    client: PulseClient,
    interaction_name: str,
    effective_date: str,
) -> RootCausePayloadSchema:
    peek = await _peek_rca_report_status(client, interaction_name, effective_date)
    if isinstance(peek, dict):
        raise _fetch_error_from_pulse_client_dict(peek)

    if peek.status_code == 200:
        body = _unwrap_pulse_server_body(peek.json())
        status = _normalize_job_status(body.get("status"))
        if status == "COMPLETED":
            return await _tabular_from_completed_rca_response(
                client, body, interaction_name, effective_date
            )
        if status == "FAILED":
            msg = str(body.get("errorMessage") or "RCA report job failed")
            raise RootCauseFetchError(HTTP_BAD_GATEWAY, msg)
        if status in ("PENDING", "PROCESSING"):
            job_id = body.get("jobId")
            if isinstance(job_id, str) and job_id.strip():
                terminal = await _wait_for_rca_job_terminal(client, job_id.strip())
                return await _tabular_from_completed_rca_response(
                    client, terminal, interaction_name, effective_date
                )
    elif peek.status_code != 404:
        raise _root_cause_fetch_error_from_http_response(peek)

    post = await _post_rca_report(client, interaction_name, effective_date)
    if isinstance(post, dict):
        raise _fetch_error_from_pulse_client_dict(post)

    if post.status_code == 200:
        completed = _unwrap_pulse_server_body(post.json())
        return await _tabular_from_completed_rca_response(
            client, completed, interaction_name, effective_date
        )
    if post.status_code == HTTP_ACCEPTED:
        body_unwrapped = _unwrap_pulse_server_body(post.json())
        job_id = body_unwrapped.get("jobId")
        if not isinstance(job_id, str) or not job_id.strip():
            raise RootCauseFetchError(
                HTTP_BAD_GATEWAY,
                "RCA async response missing jobId",
            )
        terminal = await _wait_for_rca_job_terminal(client, job_id.strip())
        return await _tabular_from_completed_rca_response(
            client, terminal, interaction_name, effective_date
        )

    raise _root_cause_fetch_error_from_http_response(post)


async def fetch_root_cause_payload(
    interaction_name: str,
    date_value: str | None,
    authorization: str,
    project_id: str,
) -> RootCausePayloadSchema:
    """
    Load root-cause tabular payload using the same async RCA pipeline as the Pulse dashboard:
    peek or POST ``/v1/ai/rca/report``, poll ``/v1/ai-rca/job/{jobId}`` when needed, then read
    embedded ``rootCausePayload`` from the completed report body. If missing (e.g. legacy cache),
    falls back to GET ``/v1/interactions/{name}/root-cause``.

    ``authorization`` must be a non-empty ``Authorization`` header value (e.g. ``Bearer <jwt>``).
    ``project_id`` is sent as ``X-Project-ID`` so pulse-server can authorize via OpenFGA.

    Uses :class:`~pulse_ai.client.pulse_client.PulseClient` (same stack as EM tools).
    """
    effective_date = _resolve_effective_date(date_value)

    async with PulseClient(
        authorization_header=authorization,
        project_id=project_id,
    ) as client:
        return await _orchestrate_rca_job_then_tabular(
            client, interaction_name, effective_date
        )
