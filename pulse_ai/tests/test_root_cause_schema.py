"""RootCausePayloadSchema accepts pulse-server JSON with null metric values."""

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


def test_segment_accepts_null_metrics_and_deltas():
    seg = RootCauseSegmentSchema(
        label="Android",
        metrics={"apdex": None, "volume": 10},
        deltas={"apdex": None},
    )
    assert seg.metrics["apdex"] is None
    assert seg.deltas["apdex"] is None
