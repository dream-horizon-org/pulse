"""RootCausePayloadSchema accepts server RCA contract values and rejects junk."""

from __future__ import annotations

import pytest
from pydantic import ValidationError

from pulse_ai.schemas.root_cause import RootCausePayloadSchema, RootCauseSegmentSchema


def test_mode_accepts_flat_hierarchical_hybrid_and_none() -> None:
    seg = RootCauseSegmentSchema(label="s", metrics={}, deltas={})
    for mode in ("flat", "hierarchical", "hybrid", None):
        p = RootCausePayloadSchema(baseline={}, segments=[seg], mode=mode)
        assert p.mode == mode


def test_mode_rejects_unknown_literal() -> None:
    with pytest.raises(ValidationError):
        RootCausePayloadSchema.model_validate(
            {
                "baseline": {},
                "segments": [{"label": "s", "metrics": {}, "deltas": {}}],
                "mode": "unified",
            }
        )
