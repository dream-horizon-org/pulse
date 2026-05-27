"""Tests for pulse_ai.server.screen_rca_runner — PII sanitization of screen narratives."""
from __future__ import annotations

from pulse_ai.schemas.screen_rca_narrative_v1 import ScreenRcaNarrativeV1
from pulse_ai.server.screen_rca_runner import _sanitize_screen_rca_narrative


class TestSanitizeScreenRcaNarrative:

    def test_executive_summary_email_is_redacted(self):
        narrative = ScreenRcaNarrativeV1(
            executive_summary="Top user is admin@corp.com with 10 rage clicks.",
            recommendations=["Fix A.", "Fix B.", "Fix C."],
        )
        result = _sanitize_screen_rca_narrative(narrative)
        assert "admin@corp.com" not in result.executive_summary
        assert "[REDACTED:EMAIL]" in result.executive_summary

    def test_recommendation_email_is_redacted(self):
        narrative = ScreenRcaNarrativeV1(
            executive_summary="Screen health is poor.",
            recommendations=[
                "Notify dev@company.com about rage clicks.",
                "Reduce interaction time.",
                "Audit slow frames.",
            ],
        )
        result = _sanitize_screen_rca_narrative(narrative)
        assert "dev@company.com" not in result.recommendations[0]
        assert "[REDACTED:EMAIL]" in result.recommendations[0]

    def test_clean_narrative_returned_unchanged(self):
        narrative = ScreenRcaNarrativeV1(
            executive_summary="P95 interaction time is 850ms.",
            recommendations=["Reduce render time.", "Optimize images.", "Remove unused JS."],
        )
        result = _sanitize_screen_rca_narrative(narrative)
        assert result.executive_summary == "P95 interaction time is 850ms."
        assert result.recommendations == [
            "Reduce render time.", "Optimize images.", "Remove unused JS."
        ]
