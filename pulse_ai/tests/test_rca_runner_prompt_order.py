"""User-message construction for RCA runner (prompt ordering)."""

from __future__ import annotations

from pulse_ai.agents.shared.schemas.root_cause import (
    RootCausePayloadSchema,
    RootCauseSegmentSchema,
)
from pulse_ai.server.rca_runner import _build_rca_prompt


def _minimal_payload() -> RootCausePayloadSchema:
    seg = RootCauseSegmentSchema(label="seg", metrics={}, deltas={})
    return RootCausePayloadSchema(
        baseline={},
        segments=[seg, seg],
        noDataAvailable=True,
        everythingGood=False,
    )


def test_error_attribution_block_before_session_evidence() -> None:
    prompt = _build_rca_prompt(
        "checkout",
        _minimal_payload(),
        example_session_ids=["sess-a", "sess-b"],
        error_attribution_payload={"relatedAttributions": []},
    )
    rc_idx = prompt.index("RootCausePayload(JSON):")
    attr_idx = prompt.index("ErrorAttributionPayload(JSON):")
    sess_idx = prompt.index("## Session Evidence")
    assert rc_idx < attr_idx < sess_idx


def test_omits_attribution_block_when_none() -> None:
    prompt = _build_rca_prompt(
        "checkout",
        _minimal_payload(),
        example_session_ids=None,
        error_attribution_payload=None,
    )
    assert "ErrorAttributionPayload(JSON)" not in prompt
