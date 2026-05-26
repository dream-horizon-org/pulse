"""HTTP-level tests for POST /interactions/overview."""
from __future__ import annotations

from unittest.mock import AsyncMock, patch

import pytest
from fastapi.testclient import TestClient

from pulse_ai.server.interactions_overview_runner import InteractionsOverviewRunnerError
from pulse_ai.server.schemas import InteractionsOverviewResponse


@pytest.fixture
def client() -> TestClient:
    from pulse_ai.server import app
    return TestClient(app)


_GOOD_HEADERS = {
    "Authorization": "Bearer test-token",
    "X-Project-ID": "proj-test",
}

_GOOD_RESPONSE = InteractionsOverviewResponse(
    summary="All 12 interactions healthy. Apdex 0.92.",
    context="Apdex 0.92 last 1h, stable from 0.91 last 24h.",
    generatedAt="2026-05-22T10:00:00+00:00",
)


# ---------------------------------------------------------------------------
# Auth / header validation
# ---------------------------------------------------------------------------

def test_missing_authorization_header_returns_401(client: TestClient) -> None:
    r = client.post(
        "/interactions/overview",
        json={},
        headers={"X-Project-ID": "proj-test"},
    )
    assert r.status_code == 401


def test_missing_project_id_header_returns_400(client: TestClient) -> None:
    r = client.post(
        "/interactions/overview",
        json={},
        headers={"Authorization": "Bearer test-token"},
    )
    assert r.status_code == 400


def test_missing_both_headers_returns_401(client: TestClient) -> None:
    r = client.post("/interactions/overview", json={})
    assert r.status_code == 401


# ---------------------------------------------------------------------------
# Happy path
# ---------------------------------------------------------------------------

def test_successful_overview_returns_200_with_fields(client: TestClient) -> None:
    with patch(
        "pulse_ai.server.routes.generate_interactions_overview",
        new=AsyncMock(return_value=_GOOD_RESPONSE),
    ):
        r = client.post("/interactions/overview", json={}, headers=_GOOD_HEADERS)

    assert r.status_code == 200
    body = r.json()
    assert body["summary"] == _GOOD_RESPONSE.summary
    assert body["context"] == _GOOD_RESPONSE.context
    assert body["generatedAt"] == _GOOD_RESPONSE.generatedAt


def test_successful_overview_with_previous_context(client: TestClient) -> None:
    with patch(
        "pulse_ai.server.routes.generate_interactions_overview",
        new=AsyncMock(return_value=_GOOD_RESPONSE),
    ) as mock_gen:
        r = client.post(
            "/interactions/overview",
            json={"previousContext": "Apdex 0.88 stable."},
            headers=_GOOD_HEADERS,
        )

    assert r.status_code == 200
    call_kwargs = mock_gen.call_args.kwargs
    assert call_kwargs["previous_context"] == "Apdex 0.88 stable."


def test_null_previous_context_passes_none(client: TestClient) -> None:
    with patch(
        "pulse_ai.server.routes.generate_interactions_overview",
        new=AsyncMock(return_value=_GOOD_RESPONSE),
    ) as mock_gen:
        r = client.post(
            "/interactions/overview",
            json={"previousContext": None},
            headers=_GOOD_HEADERS,
        )

    assert r.status_code == 200
    call_kwargs = mock_gen.call_args.kwargs
    assert call_kwargs["previous_context"] is None


# ---------------------------------------------------------------------------
# Error propagation
# ---------------------------------------------------------------------------

def test_runner_error_500_returns_500(client: TestClient) -> None:
    with patch(
        "pulse_ai.server.routes.generate_interactions_overview",
        new=AsyncMock(
            side_effect=InteractionsOverviewRunnerError(500, "Pipeline failed")
        ),
    ):
        r = client.post("/interactions/overview", json={}, headers=_GOOD_HEADERS)

    assert r.status_code == 500


def test_runner_error_504_returns_504(client: TestClient) -> None:
    with patch(
        "pulse_ai.server.routes.generate_interactions_overview",
        new=AsyncMock(
            side_effect=InteractionsOverviewRunnerError(504, "Timed out")
        ),
    ):
        r = client.post("/interactions/overview", json={}, headers=_GOOD_HEADERS)

    assert r.status_code == 504
