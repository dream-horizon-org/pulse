from __future__ import annotations

from typing import Any

from pydantic import BaseModel

from pulse_ai.schemas.rca_structured_v1 import RcaStructuredReportV1


class RcaReportRequest(BaseModel):
    entityKey: str
    rcaType: str
    date: str | None = None
    rootCausePayload: dict[str, Any] | None = None
    # Set by pulse-ui via pulse-server when forcing refresh; ignored by the pipeline.
    regenerate: bool | None = None


class ReportPayloadSchema(BaseModel):
    structured: RcaStructuredReportV1


class RcaReportResponse(BaseModel):
    report: ReportPayloadSchema
    cached: bool = False
