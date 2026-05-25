"""Tracer-bullet fixture endpoint for InteractionReportV1."""

from __future__ import annotations

from datetime import date

from pulse_ai.server.interaction_report_fixture import build_payment_gateway_fixture_report


def test_fixture_report_validates_and_has_version_one() -> None:
    report = build_payment_gateway_fixture_report(
        project_id="proj-test",
        interaction_name="PaymentGatewayHandshakeLatency",
        period_start=date(2026, 5, 1),
        period_end=date(2026, 5, 7),
    )
    assert report.version == 1
    assert report.identity.name == "PaymentGatewayHandshakeLatency"
    assert report.verdict.rating in ("red", "amber", "green")
