"""Screen-scoped RCA narrative (executive summary + recommendations only)."""

from __future__ import annotations

from pydantic import BaseModel, Field


class ScreenRcaNarrativeV1(BaseModel):
    """LLM output for screen frustration RCA — no structured metric rows."""

    version: int = Field(default=1, ge=1, le=1)
    executive_summary: str = Field(
        ...,
        description="Up to 4 sentences: where frustration concentrates (segment label, bad_frustration_percentage).",
    )
    recommendations: list[str] = Field(
        ...,
        min_length=0,
        max_length=3,
        description="Short actionable strings derived from the segment data.",
    )
