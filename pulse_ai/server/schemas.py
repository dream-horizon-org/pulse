from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field

from pulse_ai.schemas.rca_structured_v1 import RcaStructuredReportV1
from pulse_ai.schemas.screen_rca_structured_v2 import ScreenRcaStructuredV2


class RcaReportRequest(BaseModel):
    entityKey: str
    rcaType: str
    date: str | None = None
    analysisLookbackDays: int | None = Field(
        default=None,
        description="RCA telemetry window in days (pulse-server); echoed on report.",
    )
    rootCausePayload: dict[str, Any] | None = None
    errorAttributionPayload: dict[str, Any] | None = Field(
        default=None,
        description="Pre-computed drill bundle from pulse-server enrichment (same RCA window as root cause).",
    )
    # Set by pulse-ui via pulse-server when forcing refresh; ignored by the pipeline.
    regenerate: bool | None = None


class ReportPayloadSchema(BaseModel):
    structured: RcaStructuredReportV1
    analysisLookbackDays: int | None = None


class RcaReportResponse(BaseModel):
    report: ReportPayloadSchema
    cached: bool = False


class ScreenRcaV2ReportRequest(BaseModel):
    """V2: Pre-ranked problems + evidences from backend; LLM adds summary + recommendations."""

    screenName: str
    problems: list[dict[str, Any]]
    evidences: dict[str, Any]
    start: str
    end: str


class ScreenRcaV2ReportPayloadSchema(BaseModel):
    structured: ScreenRcaStructuredV2


class ScreenRcaV2ReportResponse(BaseModel):
    report: ScreenRcaV2ReportPayloadSchema
    cached: bool = False


class SessionRcaReportRequest(BaseModel):
    """Embedded rootCausePayload is required (v1); window fields are echoed into the LLM prompt."""

    rootCausePayload: dict[str, Any]
    date: str | None = None
    asOf: str | None = None


class SessionRcaReportPayloadSchema(BaseModel):
    structured: SessionRcaStructuredResponseV1


class SessionRcaReportResponse(BaseModel):
    report: SessionRcaReportPayloadSchema
    cached: bool = False
