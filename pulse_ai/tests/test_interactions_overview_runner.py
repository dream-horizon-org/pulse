"""Tests for pulse_ai.server.interactions_overview_runner."""
from __future__ import annotations

import asyncio
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from pulse_ai.constants import INTERACTIONS_OVERVIEW_PREVIOUS_CONTEXT_MAX_LEN
from pulse_ai.schemas.interaction_overview_v1 import (
    InteractionObservation,
    InteractionOverviewOutputV1,
)
from pulse_ai.server.interactions_overview_runner import (
    InteractionsOverviewRunnerError,
    _assemble_summary,
    _truncate_previous_context,
    generate_interactions_overview,
)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _make_valid_payload(**overrides) -> dict:
    """Minimal valid InteractionOverviewOutputV1 payload (no attention needed)."""
    base = {
        "poor_interactions": [],
        "fair_or_elevated_interactions": [],
        "trend_note": None,
        "business_impact": "healthy portfolio",
        "context": "Apdex 0.92 last 1h, stable.",
    }
    base.update(overrides)
    return base


def _make_session(state: dict) -> MagicMock:
    session = MagicMock()
    session.state = state
    return session


def _make_runner(session_state: dict) -> MagicMock:
    session = _make_session(session_state)
    session_service = MagicMock()
    session_service.create_session = AsyncMock(return_value=session)
    session_service.get_session = AsyncMock(return_value=session)
    session_service.delete_session = AsyncMock()

    runner = MagicMock()
    runner.app_name = "pulse_ai"
    runner.session_service = session_service

    async def _run_async(**_kwargs):
        if False:
            yield  # make it an async generator

    runner.run_async = MagicMock(side_effect=_run_async)
    return runner


# ---------------------------------------------------------------------------
# Happy path
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_happy_path_returns_summary_context_generatedAt() -> None:
    result_payload = _make_valid_payload()
    runner = _make_runner({"interactions_overview_result": result_payload})

    resp = await generate_interactions_overview(
        runner,
        bearer_token="Bearer tok",
        project_id="proj-1",
    )

    # summary is assembled by _assemble_summary() — not taken from the payload field
    assert resp.summary
    # context is sanitize_pii(validated.context) — passes through unchanged when no PII
    assert resp.context == result_payload["context"]
    assert resp.generatedAt  # non-empty ISO string


@pytest.mark.asyncio
async def test_happy_path_pii_redacted_in_summary() -> None:
    # trend_note is appended verbatim by _assemble_summary — PII will be in the assembled summary
    result_payload = _make_valid_payload(
        trend_note="User admin@corp.com reported degradation."
    )
    runner = _make_runner({"interactions_overview_result": result_payload})

    resp = await generate_interactions_overview(
        runner,
        bearer_token="Bearer tok",
        project_id="proj-1",
    )

    assert "admin@corp.com" not in resp.summary
    assert "[REDACTED:EMAIL]" in resp.summary


@pytest.mark.asyncio
async def test_happy_path_pii_redacted_in_context() -> None:
    result_payload = _make_valid_payload(
        context="Contact dev@company.com for trend data."
    )
    runner = _make_runner({"interactions_overview_result": result_payload})

    resp = await generate_interactions_overview(
        runner,
        bearer_token="Bearer tok",
        project_id="proj-1",
    )

    assert "dev@company.com" not in resp.context
    assert "[REDACTED:EMAIL]" in resp.context


# ---------------------------------------------------------------------------
# Missing structured result → 500
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_missing_result_raises_500() -> None:
    runner = _make_runner({})  # no interactions_overview_result key

    with pytest.raises(InteractionsOverviewRunnerError) as exc_info:
        await generate_interactions_overview(
            runner,
            bearer_token="Bearer tok",
            project_id="proj-1",
        )

    assert exc_info.value.status_code == 500


@pytest.mark.asyncio
async def test_none_result_raises_500() -> None:
    runner = _make_runner({"interactions_overview_result": None})

    with pytest.raises(InteractionsOverviewRunnerError) as exc_info:
        await generate_interactions_overview(
            runner,
            bearer_token="Bearer tok",
            project_id="proj-1",
        )

    assert exc_info.value.status_code == 500


# ---------------------------------------------------------------------------
# Timeout → 504
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_timeout_raises_504() -> None:
    session_service = MagicMock()
    session_service.create_session = AsyncMock(return_value=_make_session({}))
    session_service.delete_session = AsyncMock()

    runner = MagicMock()
    runner.app_name = "pulse_ai"
    runner.session_service = session_service

    async def _slow(**_kwargs):
        raise TimeoutError("timed out")
        if False:
            yield

    runner.run_async = MagicMock(side_effect=_slow)

    with patch(
        "pulse_ai.server.interactions_overview_runner.asyncio.wait_for",
        side_effect=TimeoutError,
    ):
        with pytest.raises(InteractionsOverviewRunnerError) as exc_info:
            await generate_interactions_overview(
                runner,
                bearer_token="Bearer tok",
                project_id="proj-1",
            )

    assert exc_info.value.status_code == 504


# ---------------------------------------------------------------------------
# Prompt content: cold start (no previous_context)
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_cold_start_prompt_contains_24h_instruction() -> None:
    """Without previous_context, prompt must use last_24h (cold-start)."""
    result_payload = _make_valid_payload()
    runner = _make_runner({"interactions_overview_result": result_payload})

    captured_messages: list = []
    original_run_async = runner.run_async

    async def _capture(**kwargs):
        captured_messages.append(kwargs.get("new_message"))
        if False:
            yield

    runner.run_async = MagicMock(side_effect=_capture)
    # Re-seed result so get_session still returns it
    runner.session_service.get_session = AsyncMock(
        return_value=_make_session({"interactions_overview_result": result_payload})
    )

    await generate_interactions_overview(
        runner,
        bearer_token="Bearer tok",
        project_id="proj-1",
        previous_context=None,
    )

    assert captured_messages, "run_async was not called"
    msg = captured_messages[0]
    text = msg.parts[0].text
    # Cold start: time_range="last_24h" embedded directly, cold-start noted.
    assert '"last_24h"' in text
    assert "cold" in text.lower() or "no prior baseline" in text.lower()


# ---------------------------------------------------------------------------
# Prompt content: with previous_context (one-window)
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_with_previous_context_prompt_contains_context_and_1h() -> None:
    """With previous_context, prompt should include it and use last_1h only."""
    result_payload = _make_valid_payload()
    runner = _make_runner({"interactions_overview_result": result_payload})

    captured_messages: list = []

    async def _capture(**kwargs):
        captured_messages.append(kwargs.get("new_message"))
        if False:
            yield

    runner.run_async = MagicMock(side_effect=_capture)
    runner.session_service.get_session = AsyncMock(
        return_value=_make_session({"interactions_overview_result": result_payload})
    )

    prior = "Apdex 0.88 last 1h, trend stable."
    await generate_interactions_overview(
        runner,
        bearer_token="Bearer tok",
        project_id="proj-1",
        previous_context=prior,
    )

    assert captured_messages, "run_async was not called"
    text = captured_messages[0].parts[0].text
    assert prior in text
    # Incremental: last_1h is the primary window; last_24h may appear as fallback instruction.
    assert '"last_1h"' in text
    # last_24h may appear only as fallback — verify last_1h precedes last_24h in text
    idx_1h = text.find('"last_1h"')
    idx_24h = text.find('"last_24h"')
    assert idx_1h < idx_24h or idx_24h == -1, "last_1h must be primary (appear before any last_24h)"


# ---------------------------------------------------------------------------
# State seeding
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_session_created_with_bearer_and_project_in_state() -> None:
    """create_session must be called with bearer_token and project_id in state."""
    runner = _make_runner({"interactions_overview_result": _make_valid_payload()})

    await generate_interactions_overview(
        runner,
        bearer_token="Bearer mytoken",
        project_id="proj-xyz",
    )

    create_call = runner.session_service.create_session.call_args
    state_arg = create_call.kwargs.get("state") or {}
    assert state_arg.get("bearer_token") == "Bearer mytoken"
    assert state_arg.get("project_id") == "proj-xyz"


# ---------------------------------------------------------------------------
# Session cleanup
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_ephemeral_session_deleted_on_success() -> None:
    runner = _make_runner({"interactions_overview_result": _make_valid_payload()})

    await generate_interactions_overview(
        runner,
        bearer_token="Bearer tok",
        project_id="proj-1",
    )

    assert runner.session_service.delete_session.called


@pytest.mark.asyncio
async def test_cleanup_error_does_not_propagate() -> None:
    """delete_session failure must not bubble up."""
    runner = _make_runner({"interactions_overview_result": _make_valid_payload()})
    runner.session_service.delete_session = AsyncMock(side_effect=RuntimeError("db gone"))

    # Should not raise, and summary must be populated
    resp = await generate_interactions_overview(
        runner,
        bearer_token="Bearer tok",
        project_id="proj-1",
    )
    assert resp.summary


# ---------------------------------------------------------------------------
# Malformed structured result → 500
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_malformed_result_raises_500() -> None:
    """When ADK stores a value that fails schema validation, must raise 500.

    Missing required fields (poor_interactions, fair_or_elevated_interactions,
    business_impact) triggers ValidationError.
    """
    malformed_payload = {"context": "Should fail — missing required fields."}
    runner = _make_runner({"interactions_overview_result": malformed_payload})

    with pytest.raises(InteractionsOverviewRunnerError) as exc_info:
        await generate_interactions_overview(
            runner,
            bearer_token="Bearer tok",
            project_id="proj-1",
        )

    assert exc_info.value.status_code == 500
    assert "invalid structured payload" in exc_info.value.message.lower()


# ---------------------------------------------------------------------------
# Session cleanup on error paths (try/finally)
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_session_deleted_on_timeout() -> None:
    """delete_session must be called even when the pipeline times out."""
    session_service = MagicMock()
    session_service.create_session = AsyncMock(return_value=_make_session({}))
    session_service.delete_session = AsyncMock()

    runner = MagicMock()
    runner.app_name = "pulse_ai"
    runner.session_service = session_service

    with patch(
        "pulse_ai.server.interactions_overview_runner.asyncio.wait_for",
        side_effect=TimeoutError,
    ):
        with pytest.raises(InteractionsOverviewRunnerError) as exc_info:
            await generate_interactions_overview(
                runner,
                bearer_token="Bearer tok",
                project_id="proj-1",
            )

    assert exc_info.value.status_code == 504
    assert session_service.delete_session.called


@pytest.mark.asyncio
async def test_session_deleted_on_missing_result() -> None:
    """delete_session must be called when structured result is absent."""
    runner = _make_runner({})  # no interactions_overview_result

    with pytest.raises(InteractionsOverviewRunnerError) as exc_info:
        await generate_interactions_overview(
            runner,
            bearer_token="Bearer tok",
            project_id="proj-1",
        )

    assert exc_info.value.status_code == 500
    assert runner.session_service.delete_session.called


@pytest.mark.asyncio
async def test_session_deleted_on_validation_error() -> None:
    """delete_session must be called when schema validation fails."""
    malformed = {"context": "bad — missing required fields"}
    runner = _make_runner({"interactions_overview_result": malformed})

    with pytest.raises(InteractionsOverviewRunnerError) as exc_info:
        await generate_interactions_overview(
            runner,
            bearer_token="Bearer tok",
            project_id="proj-1",
        )

    assert exc_info.value.status_code == 500
    assert runner.session_service.delete_session.called


# ---------------------------------------------------------------------------
# previousContext truncation
# ---------------------------------------------------------------------------

def test_truncate_previous_context_none_returns_none() -> None:
    assert _truncate_previous_context(None) is None


def test_truncate_previous_context_short_returns_unchanged() -> None:
    value = "Short context."
    assert _truncate_previous_context(value) == value


def test_truncate_previous_context_at_limit_returns_unchanged() -> None:
    value = "x" * INTERACTIONS_OVERVIEW_PREVIOUS_CONTEXT_MAX_LEN
    assert _truncate_previous_context(value) == value


def test_truncate_previous_context_over_limit_is_capped() -> None:
    value = "x" * (INTERACTIONS_OVERVIEW_PREVIOUS_CONTEXT_MAX_LEN + 100)
    result = _truncate_previous_context(value)
    assert result is not None
    assert len(result) == INTERACTIONS_OVERVIEW_PREVIOUS_CONTEXT_MAX_LEN


@pytest.mark.asyncio
async def test_oversized_previous_context_is_truncated_before_prompt() -> None:
    """Oversized previousContext must not appear verbatim in the prompt."""
    result_payload = _make_valid_payload()
    runner = _make_runner({"interactions_overview_result": result_payload})

    captured: list = []

    async def _capture(**kwargs):
        captured.append(kwargs.get("new_message"))
        if False:
            yield

    runner.run_async = MagicMock(side_effect=_capture)
    runner.session_service.get_session = AsyncMock(
        return_value=_make_session({"interactions_overview_result": result_payload})
    )

    long_context = "A" * (INTERACTIONS_OVERVIEW_PREVIOUS_CONTEXT_MAX_LEN + 200)
    await generate_interactions_overview(
        runner,
        bearer_token="Bearer tok",
        project_id="proj-1",
        previous_context=long_context,
    )

    assert captured
    text = captured[0].parts[0].text
    # Prompt must not contain the full oversize string
    assert long_context not in text
    # But it must contain a truncated prefix
    assert "A" * INTERACTIONS_OVERVIEW_PREVIOUS_CONTEXT_MAX_LEN in text


# ---------------------------------------------------------------------------
# _assemble_summary unit tests (pure function — no runner needed)
# ---------------------------------------------------------------------------

def _make_validated(**overrides) -> InteractionOverviewOutputV1:
    base = dict(
        poor_interactions=[],
        fair_or_elevated_interactions=[],
        trend_note=None,
        business_impact="healthy portfolio",
        context="snapshot.",
    )
    base.update(overrides)
    return InteractionOverviewOutputV1(**base)


def _poor_obs(name: str) -> InteractionObservation:
    return InteractionObservation(interaction_name=name, hypothesis="slow backend")


def _fair_obs(name: str) -> InteractionObservation:
    return InteractionObservation(interaction_name=name, hypothesis="elevated errors")


def _make_health_row(name: str, *, severity: str, error_severity: str = "NORMAL_ERROR_RATE",
                     apdex: float = 0.5, poor_user_rate: float | None = None,
                     error_rate: float | None = None, priority_rank: int | None = None,
                     total_categorized: int = 5000) -> dict:
    return {
        "interaction_name": name,
        "severity": severity,
        "error_severity": error_severity,
        "apdex": apdex,
        "apdex_str": f"{apdex:.2f}",
        "poor_user_rate": poor_user_rate,
        "poor_user_rate_str": f"{poor_user_rate:.1f}%" if poor_user_rate is not None else "N/A",
        "error_rate": error_rate,
        "error_rate_str": f"{error_rate:.1f}%" if error_rate is not None else "N/A",
        "priority_rank": priority_rank,
        "total_categorized": total_categorized,
    }


def test_assemble_summary_all_healthy_returns_performing_well() -> None:
    validated = _make_validated()
    health_data = {
        "login": _make_health_row("login", severity="EXCELLENT", apdex=0.95),
        "checkout": _make_health_row("checkout", severity="GOOD", apdex=0.75),
    }
    result = _assemble_summary(validated, health_data)
    assert "performing well" in result
    assert "2 tracked" in result


def test_assemble_summary_headline_uses_described_count_not_health_data() -> None:
    """Headline count must match what's actually in the clauses (LLM fields),
    not the broader health_data count. Regression for #1."""
    # health_data has 3 rows that need attention, but LLM only described 1 POOR
    poor_row = _make_health_row("slow_screen", severity="POOR", apdex=0.3,
                                poor_user_rate=40.0, error_rate=5.0, priority_rank=1)
    fair_row = _make_health_row("ok_screen", severity="FAIR", apdex=0.5,
                                error_severity="ELEVATED_ERROR_RATE", error_rate=15.0)
    extra_row = _make_health_row("another_screen", severity="POOR", apdex=0.2,
                                 poor_user_rate=60.0, error_rate=30.0)
    # LLM only returned 1 POOR — omitted another_screen
    validated = _make_validated(
        poor_interactions=[_poor_obs("slow_screen")],
        fair_or_elevated_interactions=[_fair_obs("ok_screen")],
        business_impact="blocking key flows",
    )
    health_data = {
        "slow_screen": poor_row,
        "ok_screen": fair_row,
        "another_screen": extra_row,
    }
    result = _assemble_summary(validated, health_data)
    # Described: 1 POOR + 1 FAIR = 2; must NOT say 3
    assert result.startswith("2 interactions need attention")


def test_assemble_summary_priority_sentence_single_item() -> None:
    validated = _make_validated(
        poor_interactions=[_poor_obs("payment_flow")],
        business_impact="blocking purchases",
    )
    health_data = {
        "payment_flow": _make_health_row(
            "payment_flow", severity="POOR", apdex=0.3,
            poor_user_rate=50.0, error_rate=35.0, priority_rank=1,
            total_categorized=8000,
        ),
    }
    result = _assemble_summary(validated, health_data)
    assert "Prioritize fixing" in result
    assert "payment_flow" in result


def test_assemble_summary_priority_sentence_multiple_items() -> None:
    validated = _make_validated(
        poor_interactions=[_poor_obs("app_launch"), _poor_obs("payment_flow")],
        business_impact="blocking core flows",
    )
    health_data = {
        "app_launch": _make_health_row(
            "app_launch", severity="POOR", apdex=0.35,
            poor_user_rate=45.0, error_rate=10.0, priority_rank=1,
            total_categorized=20000,
        ),
        "payment_flow": _make_health_row(
            "payment_flow", severity="POOR", apdex=0.25,
            poor_user_rate=60.0, error_rate=35.0, priority_rank=2,
            total_categorized=5000,
        ),
    }
    result = _assemble_summary(validated, health_data)
    assert "Prioritize fixing" in result
    assert "app_launch" in result
    assert "payment_flow" in result
    assert "first" in result


def test_assemble_summary_poor_rate_sub_one_percent_renders_lt_1() -> None:
    """poor_user_rate < 1 must render as '< 1%', not '0%'. Regression for #3."""
    validated = _make_validated(
        poor_interactions=[_poor_obs("obscure_flow")],
        business_impact="minor impact",
    )
    health_data = {
        "obscure_flow": _make_health_row(
            "obscure_flow", severity="POOR", apdex=0.3,
            poor_user_rate=0.4, error_rate=5.0, priority_rank=1,
            total_categorized=2000,
        ),
    }
    result = _assemble_summary(validated, health_data)
    assert "0% poor experience" not in result
    assert "< 1% poor experience" in result
