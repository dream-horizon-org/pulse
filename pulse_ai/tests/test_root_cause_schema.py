"""RootCausePayloadSchema accepts pulse-server JSON with null metric values."""

"""RootCausePayloadSchema accepts server RCA contract values and rejects junk."""

from __future__ import annotations

import pytest
from pydantic import ValidationError

from pulse_ai.schemas.root_cause import RootCausePayloadSchema, RootCauseSegmentSchema


def test_payload_accepts_null_baseline_and_segment_metrics():
    raw = {
        "baseline": {
            "volume": 0,
            "apdex": None,
            "error_rate": None,
            "poor_user_pct": None,
            "crash_rate": None,
        },
        "segments": [],
        "noDataAvailable": True,
    }
    model = RootCausePayloadSchema.model_validate(raw)
    assert model.baseline["apdex"] is None
    assert model.segments == []
def test_mode_accepts_flat_hierarchical_hybrid_and_none() -> None:
    seg = RootCauseSegmentSchema(label="s", metrics={}, deltas={})
    for mode in ("flat", "hierarchical", "hybrid", None):
        p = RootCausePayloadSchema(baseline={}, segments=[seg], mode=mode)
        assert p.mode == mode


def test_segment_accepts_null_metrics_and_deltas():
    seg = RootCauseSegmentSchema(
        label="Android",
        metrics={"apdex": None, "volume": 10},
        deltas={"apdex": None},
    )
    assert seg.metrics["apdex"] is None
    assert seg.deltas["apdex"] is None
def test_mode_rejects_unknown_literal() -> None:
    with pytest.raises(ValidationError):
        RootCausePayloadSchema.model_validate(
            {
                "baseline": {},
                "segments": [{"label": "s", "metrics": {}, "deltas": {}}],
                "mode": "unified",
            }
        )
