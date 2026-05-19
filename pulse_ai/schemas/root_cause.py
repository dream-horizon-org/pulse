from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class RootCauseSegmentSchema(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    label: str
    dimensions: dict[str, str] | None = None
    metrics: dict[str, int | float | str]
    deltas: dict[str, float]
    exampleSessionIds: list[str] | None = Field(None, alias="exampleSessionIds")
    serverRank: int | None = Field(
        None,
        alias="serverRank",
        description=(
            "1-based Pulse priority after server merge/sort (lower = more important). "
            "Aligns with final segment list order: hybrid mode lists 2D+ segments before 1D flat."
        ),
    )


class RootCausePayloadSchema(BaseModel):
    baseline: dict[str, int | float | str]
    segments: list[RootCauseSegmentSchema]
    mode: Literal["hierarchical", "flat", "hybrid"] | None = None
    cachedAt: str | None = None
    everythingGood: bool | None = None
    noDataAvailable: bool | None = None
    message: str | None = None
