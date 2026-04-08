from __future__ import annotations

import asyncio
import json
import logging
import uuid
from typing import Any

from google.genai.types import Content

from pulse_ai.agents.rca.report_event_parts import extract_structured_rca_report_from_event_parts
from pulse_ai.constants import (
    HTTP_TIMEOUT_GATEWAY,
    RCA_PIPELINE_TIMEOUT_SECONDS,
    RCA_REPORT_AGENT_NAME,
    USER_ID_RCA,
)
from pulse_ai.schemas import RootCausePayloadSchema
from pulse_ai.schemas.rca_structured_v1 import RcaStructuredReportV1
from pulse_ai.server.schemas import ReportPayloadSchema, RcaReportResponse

logger = logging.getLogger(__name__)


class RcaRunnerError(Exception):
    def __init__(self, status_code: int, message: str) -> None:
        super().__init__(message)
        self.status_code = status_code
        self.message = message


def _build_rca_prompt(
    interaction_name: str,
    payload: RootCausePayloadSchema,
    example_session_ids: list[str] | None = None,
) -> str:
    serialized_payload = json.dumps(payload.model_dump(), ensure_ascii=True)
    
    sessions_context = ""
    if example_session_ids and len(example_session_ids) > 0:
        sessions_list = ', '.join([f'"{sid}"' for sid in example_session_ids])
        sessions_context = (
            f"\n## Session Evidence\n"
            f"Available sessions for replay: [{sessions_list}]\n"
            f"For each segment, select 1-2 most relevant session IDs that demonstrate the issue.\n"
            f"Include in 'affected_sessions' field as an array (e.g., {{'affected_sessions': ['{example_session_ids[0]}']}})"
        )
    
    return (
        "Generate a root cause analysis report for the given interaction.\n"
        f"Interaction: {interaction_name}\n"
        f"RootCausePayload(JSON): {serialized_payload}"
        f"{sessions_context}"
    )


async def generate_rca_report(
    runner: Any,
    payload: RootCausePayloadSchema,
    interaction_name: str,
    example_session_ids: list[str] | None = None,
) -> RcaReportResponse:
    """
    Runs the RCA pipeline in one shot and returns typed report response.

    Timeout behavior:
    - Uses RCA_PIPELINE_TIMEOUT_SECONDS.
    - Raises RcaRunnerError(504) on timeout.
    """
    session_id = str(uuid.uuid4())
    prompt = _build_rca_prompt(interaction_name, payload, example_session_ids)
    message = Content.model_validate(
        {"role": "user", "parts": [{"text": prompt}]},
    )

    structured_report: RcaStructuredReportV1 | None = None

    async def _run() -> None:
        nonlocal structured_report
        async for event in runner.run_async(
            user_id=USER_ID_RCA,
            session_id=session_id,
            new_message=message,
        ):
            if not event.content or not event.content.parts:
                continue

            structured_part = extract_structured_rca_report_from_event_parts(
                event.content.parts,
            )
            if structured_part is not None and event.author == RCA_REPORT_AGENT_NAME:
                structured_report = structured_part

    try:
        await asyncio.wait_for(_run(), timeout=RCA_PIPELINE_TIMEOUT_SECONDS)
    except TimeoutError as error:
        raise RcaRunnerError(HTTP_TIMEOUT_GATEWAY, "RCA report generation timed out") from error
    except Exception as error:  # noqa: BLE001
        logger.exception("RCA pipeline execution failed")
        raise RcaRunnerError(500, "RCA report generation failed") from error

    if structured_report is None:
        raise RcaRunnerError(500, "RCA report missing structured payload")

    report_payload = ReportPayloadSchema(structured=structured_report)
    return RcaReportResponse(report=report_payload, cached=False)
