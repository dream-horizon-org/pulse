"""Parse ADK event parts for structured RCA report v1 from tool responses."""

from __future__ import annotations

import logging
from typing import Any

from pydantic import ValidationError

from pulse_ai.schemas.rca_structured_v1 import RcaStructuredReportV1

logger = logging.getLogger(__name__)


def extract_structured_rca_report_from_event_parts(
    event_parts: Any,
) -> RcaStructuredReportV1 | None:
    structured: RcaStructuredReportV1 | None = None

    for part in event_parts:
        if not part.function_response:
            continue
        response = part.function_response.response
        if not isinstance(response, dict):
            continue
        if response.get("success") is not True:
            continue
        structured_raw = response.get("structured")
        if not isinstance(structured_raw, dict):
            continue
        try:
            structured = RcaStructuredReportV1.model_validate(structured_raw)
        except ValidationError:
            logger.warning(
                "Ignoring invalid structured RCA payload from tool response",
                exc_info=True,
            )

    return structured
