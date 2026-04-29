"""Screen-scoped RCA narrative (executive summary + recommendations only)."""

from __future__ import annotations

from pydantic import BaseModel, Field


class ScreenRcaNarrativeV1(BaseModel):
    """LLM output for screen frustration RCA — no structured metric rows."""

    version: int = Field(default=1, ge=1, le=1)
    executive_summary: str = Field(
        ...,
        description="Up to 4 sentences: overall screen health, worst segment, scope.",
    )
    recommendations: list[str] = Field(
        ...,
        min_length=3,
        max_length=7,
        description="Short actionable strings derived from the segment data.",
    )
