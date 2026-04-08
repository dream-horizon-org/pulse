from __future__ import annotations

import asyncio
import json
import logging
import uuid
from typing import Any

from google.genai.types import Content

from pulse_ai.constants import (
    HTTP_TIMEOUT_GATEWAY,
    RCA_PIPELINE_TIMEOUT_SECONDS,
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


def _build_rca_prompt(interaction_name: str, payload: RootCausePayloadSchema) -> str:
    serialized_payload = json.dumps(payload.model_dump(), ensure_ascii=True)
    return (
        "Generate a root cause analysis report for the given interaction.\n"
        f"Interaction: {interaction_name}\n"
        f"RootCausePayload(JSON): {serialized_payload}"
    )


async def generate_rca_report(
    runner: Any,
    payload: RootCausePayloadSchema,
    interaction_name: str,
) -> RcaReportResponse:
    """
    Runs the RCA pipeline in one shot and returns typed report response.

    Timeout behavior:
    - Uses RCA_PIPELINE_TIMEOUT_SECONDS.
    - Raises RcaRunnerError(504) on timeout.
    """
    session_id = str(uuid.uuid4())
    prompt = _build_rca_prompt(interaction_name, payload)
    message = Content.model_validate(
        {"role": "user", "parts": [{"text": prompt}]},
    )

    async def _run() -> None:
        async for _ in runner.run_async(
            user_id=USER_ID_RCA,
            session_id=session_id,
            new_message=message,
        ):
            pass

    try:
        await asyncio.wait_for(_run(), timeout=RCA_PIPELINE_TIMEOUT_SECONDS)
    except TimeoutError as error:
        raise RcaRunnerError(HTTP_TIMEOUT_GATEWAY, "RCA report generation timed out") from error
    except Exception as error:  # noqa: BLE001
        logger.exception("RCA pipeline execution failed")
        raise RcaRunnerError(500, "RCA report generation failed") from error

    # Read structured report from session state set by rca_formatter_agent (output_schema)
    session = await runner.session_service.get_session(
        app_name=runner.app_name,
        user_id=USER_ID_RCA,
        session_id=session_id,
    )

    structured_report: RcaStructuredReportV1 | None = None
    if session:
        raw = session.state.get("rca_structured_report")
        if raw:
            try:
                structured_report = RcaStructuredReportV1.model_validate(raw)
            except Exception:
                logger.warning("Failed to validate structured report from session state", exc_info=True)

    if structured_report is None:
        logger.error("RCA structured payload missing, session_id=%s", session_id)
        raise RcaRunnerError(500, "RCA report missing structured payload")

    report_payload = ReportPayloadSchema(structured=structured_report)
    return RcaReportResponse(report=report_payload, cached=False)
