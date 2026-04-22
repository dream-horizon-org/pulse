from __future__ import annotations

import asyncio
import json
import logging
import uuid
from typing import Any

from google.genai.types import Content
from pydantic import ValidationError

from pulse_ai.constants import (
    HTTP_TIMEOUT_GATEWAY,
    RCA_PIPELINE_TIMEOUT_SECONDS,
    USER_ID_RCA,
)
from pulse_ai.schemas import RootCausePayloadSchema
from pulse_ai.schemas.rca_structured_v1 import RcaStructuredReportV1
from pulse_ai.server.schemas import ReportPayloadSchema, RcaReportResponse

logger = logging.getLogger(__name__)

# Retry configuration for schema validation failures
MAX_RETRIES = 2


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


async def _run_single_attempt(
    runner: Any,
    session_id: str,
    message: Content,
) -> RcaStructuredReportV1 | None:
    """Run RCA agent once and return validated report, or None if validation fails."""
    async def _run() -> None:
        async for _ in runner.run_async(
            user_id=USER_ID_RCA,
            session_id=session_id,
            new_message=message,
        ):
            pass

    try:
        await asyncio.wait_for(_run(), timeout=RCA_PIPELINE_TIMEOUT_SECONDS)
    except TimeoutError:
        logger.warning("RCA attempt timed out, session_id=%s", session_id)
        return None
    except Exception:
        logger.exception("RCA attempt failed, session_id=%s", session_id)
        return None

    # Read and validate structured report from session state
    session = await runner.session_service.get_session(
        app_name=runner.app_name,
        user_id=USER_ID_RCA,
        session_id=session_id,
    )

    if not session:
        logger.warning("No session found for attempt, session_id=%s", session_id)
        return None

    raw = session.state.get("rca_structured_report")
    if not raw:
        logger.warning("No structured report in session state, session_id=%s", session_id)
        return None

    try:
        return RcaStructuredReportV1.model_validate(raw)
    except ValidationError as e:
        logger.warning(
            "Schema validation failed for attempt, session_id=%s, errors=%s",
            session_id,
            e.errors(),
        )
        return None


async def generate_rca_report(
    runner: Any,
    payload: RootCausePayloadSchema,
    interaction_name: str,
    example_session_ids: list[str] | None = None,
) -> RcaReportResponse:
    """
    Runs the RCA pipeline with retries and returns typed report response.

    Timeout behavior:
    - Uses RCA_PIPELINE_TIMEOUT_SECONDS per attempt.
    - Raises RcaRunnerError(504) on timeout.

    Retry behavior:
    - Retries up to MAX_RETRIES on schema validation failure.
    - Uses fresh session per attempt (LLM is non-deterministic).
    """
    prompt = _build_rca_prompt(interaction_name, payload, example_session_ids)
    message = Content.model_validate(
        {"role": "user", "parts": [{"text": prompt}]},
    )

    structured_report: RcaStructuredReportV1 | None = None
    last_error: Exception | None = None

    for attempt in range(MAX_RETRIES):
        session_id = str(uuid.uuid4())
        logger.debug("RCA attempt %d/%d, session_id=%s", attempt + 1, MAX_RETRIES, session_id)

        structured_report = await _run_single_attempt(runner, session_id, message)

        if structured_report is not None:
            # Success - clean up session and return
            try:
                await runner.session_service.delete_session(
                    app_name=runner.app_name,
                    user_id=USER_ID_RCA,
                    session_id=session_id,
                )
            except Exception:
                logger.warning("Failed to delete ephemeral RCA session %s", session_id, exc_info=True)
            break

        # Failed - clean up session before retry
        try:
            await runner.session_service.delete_session(
                app_name=runner.app_name,
                user_id=USER_ID_RCA,
                session_id=session_id,
            )
        except Exception:
            pass  # Ignore cleanup errors on failed attempts

        if attempt < MAX_RETRIES - 1:
            logger.info("RCA attempt %d failed, retrying...", attempt + 1)
        else:
            logger.error("All %d RCA attempts failed", MAX_RETRIES)

    if structured_report is None:
        raise RcaRunnerError(500, "RCA report generation failed after retries")

    report_payload = ReportPayloadSchema(structured=structured_report)
    response = RcaReportResponse(report=report_payload, cached=False)

    return response
