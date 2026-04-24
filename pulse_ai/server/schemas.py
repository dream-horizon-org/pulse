from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field

from pulse_ai.agents.shared.schemas.rca_structured_v1 import RcaStructuredReportV1


class RcaReportRequest(BaseModel):
    interactionName: str
    date: str | None = None
    rootCausePayload: dict[str, Any] | None = None
    errorAttributionPayload: dict[str, Any] | None = Field(
        default=None,
        description="Pre-computed drill bundle from pulse-server enrichment (same RCA window as root cause).",
    )
    # Set by pulse-ui via pulse-server when forcing refresh; ignored by the pipeline.
    regenerate: bool | None = None


class ReportPayloadSchema(BaseModel):
    structured: RcaStructuredReportV1


class RcaReportResponse(BaseModel):
    report: ReportPayloadSchema
    cached: bool = False
