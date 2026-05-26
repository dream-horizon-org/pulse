from __future__ import annotations

import asyncio
import logging
import uuid
from datetime import datetime, timezone
from typing import Any

from google.genai.types import Content

from pulse_ai.constants import (
    APP_NAME,
    INTERACTIONS_OVERVIEW_PREVIOUS_CONTEXT_MAX_LEN,
    INTERACTIONS_OVERVIEW_TIMEOUT_SECONDS,
    USER_ID_INTERACTIONS_OVERVIEW,
)
from pulse_ai.output_guard import sanitize_pii
from pulse_ai.schemas.interaction_overview_v1 import (
    InteractionObservation,
    InteractionOverviewOutputV1,
)
from pulse_ai.server.schemas import InteractionsOverviewResponse

logger = logging.getLogger(__name__)


class InteractionsOverviewRunnerError(Exception):
    def __init__(self, status_code: int, message: str) -> None:
        super().__init__(message)
        self.status_code = status_code
        self.message = message


# ── Summary assembly ──────────────────────────────────────────────────────────


def _find_hypothesis(observations: list[InteractionObservation], name: str) -> str:
    for obs in observations:
        if obs.interaction_name == name:
            return obs.hypothesis
    return ""


def _join_names(names: list[str]) -> str:
    if len(names) == 1:
        return names[0]
    if len(names) == 2:
        return f"{names[0]} and {names[1]}"
    return ", ".join(names[:-1]) + f", and {names[-1]}"


def _assemble_summary(
    validated: InteractionOverviewOutputV1,
    health_data: dict[str, dict],
) -> str:
    """Build the user-facing summary in condensed grouped prose.

    Format:
      "{N} interactions need attention — {POOR detail}, while {FAIR group with range},
       and even {GOOD-elevated group} show elevated errors, {business_impact}."

    LLM contributes only qualitative hypothesis and business_impact — no numbers.
    All numeric values come from health_data (set by _enrich_health_row).
    """
    all_rows = list(health_data.values())
    total = len(all_rows)

    needs_attention = [
        r for r in all_rows
        if r.get("severity") == "POOR"
        or r.get("error_severity") in ("ELEVATED_ERROR_RATE", "CRITICAL_ERROR_RATE")
    ]
    attention_count = len(needs_attention)

    if attention_count == 0:
        parts = [f"All {total} tracked interactions are performing well with no elevated errors."]
        if validated.trend_note:
            parts.append(validated.trend_note)
        return " ".join(parts)

    # ── Build the headline clause ─────────────────────────────────────────────
    noun = "interaction" if attention_count == 1 else "interactions"
    headline = f"{attention_count} {noun} need attention"

    # ── POOR group — grouped with apdex range + poor_user_rate range ─────────
    poor_names = [obs.interaction_name for obs in validated.poor_interactions]
    poor_clause = ""
    if poor_names:
        poor_rows_data = [health_data.get(n) or {} for n in poor_names]

        apdex_vals = [r.get("apdex") or 0.0 for r in poor_rows_data]
        if len(apdex_vals) == 1:
            apdex_range_str = poor_rows_data[0].get("apdex_str", "N/A")
        else:
            apdex_range_str = f"{min(apdex_vals):.2f}–{max(apdex_vals):.2f}"

        poor_rate_vals = [r.get("poor_user_rate") for r in poor_rows_data if r.get("poor_user_rate") is not None]
        if len(poor_rate_vals) == 1:
            poor_rate_range_str = f"{poor_rate_vals[0]:.0f}%"
        elif poor_rate_vals:
            poor_rate_range_str = f"{min(poor_rate_vals):.0f}–{max(poor_rate_vals):.0f}%"
        else:
            poor_rate_range_str = "N/A"

        critical_rates = [r.get("error_rate") for r in poor_rows_data
                          if r.get("error_severity") == "CRITICAL_ERROR_RATE" and r.get("error_rate")]
        error_detail = ""
        if critical_rates:
            n_in = round(100 / max(critical_rates))
            error_detail = f", 1 in {n_in} errors"

        verb = "are" if len(poor_names) > 1 else "is"
        poor_clause = (
            f"{_join_names(poor_names)} {verb} critically broken "
            f"(Apdex {apdex_range_str}, {poor_rate_range_str} poor experience{error_detail})"
        )
        if validated.business_impact:
            poor_clause += f" — {validated.business_impact}"

    # ── FAIR group — grouped by severity, show error rate range ──────────────
    fair_rows = [
        health_data.get(obs.interaction_name) or {}
        for obs in validated.fair_or_elevated_interactions
        if (health_data.get(obs.interaction_name) or {}).get("severity") == "FAIR"
    ]
    fair_names = [r["interaction_name"] for r in fair_rows if r.get("interaction_name")]
    fair_error_rates = [r["error_rate"] for r in fair_rows if r.get("error_rate") is not None]

    good_elevated_rows = [
        health_data.get(obs.interaction_name) or {}
        for obs in validated.fair_or_elevated_interactions
        if (health_data.get(obs.interaction_name) or {}).get("severity") == "GOOD"
    ]
    good_elevated_names = [r["interaction_name"] for r in good_elevated_rows if r.get("interaction_name")]

    # ── Assemble flowing sentence ─────────────────────────────────────────────
    clauses: list[str] = []

    if poor_clause:
        clauses.append(poor_clause)

    if fair_names:
        if len(fair_error_rates) >= 2:
            lo = min(fair_error_rates)
            hi = max(fair_error_rates)
            rate_str = f"{lo:.0f}–{hi:.0f}%"
        elif fair_error_rates:
            rate_str = f"{fair_error_rates[0]:.0f}%"
        else:
            rate_str = "elevated"
        clauses.append(
            f"while {_join_names(fair_names)} {'carry' if len(fair_names) > 1 else 'carries'} "
            f"dangerously high error rates ({rate_str})"
        )

    if good_elevated_names:
        clauses.append(
            f"even the GOOD-state {_join_names(good_elevated_names)} "
            f"{'show' if len(good_elevated_names) > 1 else 'shows'} elevated errors"
        )

    if clauses:
        summary = headline + " — " + ", ".join(clauses) + "."
    else:
        summary = headline + "."

    # ── Trend note ────────────────────────────────────────────────────────────
    parts = [summary]
    if validated.trend_note:
        parts.append(validated.trend_note)

    # ── Priority sentence (top 3 max, 100% Python) ───────────────────────────
    priority_rows = sorted(
        [r for r in all_rows if r.get("priority_rank") is not None],
        key=lambda r: r["priority_rank"],
    )
    if priority_rows:
        def _user_str(total_cat: int) -> str:
            if total_cat >= 1000:
                val = total_cat / 1000
                return f"{val:.1f}k users" if val % 1 != 0 else f"{int(val)}k users"
            return f"{total_cat} users"

        def _fmt_priority(r: dict, idx: int) -> str:
            sev = r.get("severity", "POOR")
            err_str = r.get("error_rate_str", "N/A")
            total_cat = r.get("total_categorized") or 0

            if idx == 0 and len(priority_rows) > 1:
                next_cat = priority_rows[1].get("total_categorized") or 0
                # Volume drove this rank if it has >20% more users than the next item
                if total_cat > 0 and next_cat > 0 and (total_cat - next_cat) / total_cat > 0.20:
                    return f"{r['interaction_name']} ({sev}: {_user_str(total_cat)}, {err_str} errors — prioritized by user volume)"
                else:
                    return f"{r['interaction_name']} ({sev}: {err_str} errors, {_user_str(total_cat)})"
            elif sev == "POOR" and idx > 0:
                prev_cat = priority_rows[idx - 1].get("total_categorized") or 0
                if prev_cat > 0 and total_cat > 0 and (prev_cat - total_cat) / prev_cat > 0.20:
                    return f"{r['interaction_name']} ({sev}: {err_str} errors — highest error rate)"
                else:
                    return f"{r['interaction_name']} ({sev}: {err_str} errors, {_user_str(total_cat)})"
            else:
                return f"{r['interaction_name']} ({sev}: {err_str} errors)"

        fmt_parts = [_fmt_priority(r, i) for i, r in enumerate(priority_rows)]
        if len(fmt_parts) == 1:
            parts.append(f"Prioritize fixing {fmt_parts[0]}.")
        else:
            rest = ", then ".join(fmt_parts[1:])
            parts.append(f"Prioritize fixing {fmt_parts[0]} first, then investigate {rest}.")

    return " ".join(parts)


# ── Pipeline message builder ──────────────────────────────────────────────────


def _build_interactions_overview_message(
    previous_context: str | None,
) -> Content:
    if previous_context:
        context_block = f"\n\nPrevious snapshot for trend comparison:\n{previous_context}"
        run_note = "(incremental update — prior baseline provided above)"
        step2 = (
            'Call query_interaction_health(top_n=200, time_range="last_1h") for the near-real-time window. '
            'If that returns NO data or fewer than 3 interactions, also call '
            'query_interaction_health(top_n=200, time_range="last_24h") as fallback and note in the '
            'summary which window was used. Do NOT call per-interaction individually.'
        )
    else:
        context_block = ""
        run_note = "(cold-start — no prior baseline exists)"
        step2 = (
            'Call query_interaction_health(top_n=200, time_range="last_24h") — '
            "single bulk call. Do NOT call per-interaction individually."
        )

    text = (
        f"AUTOMATED PIPELINE MODE — do NOT ask follow-up questions or request clarification. "
        f"Gather all data silently and produce a complete structured report.\n\n"
        f"Task: Comprehensive interaction health overview for this project {run_note}.\n\n"
        f"Steps (execute in order, no skipping):\n"
        f"1. Call query_interactions(scope=\"list\") to get ALL interaction names in the project.\n"
        f"2. {step2}\n"
        f"3. Pass all collected data to the summary agent.\n\n"
        f"For each interaction, the tool already returns pre-computed fields: "
        f"severity (EXCELLENT/GOOD/FAIR/POOR), poor_user_rate (%), error_rate (%), "
        f"apdex, apdex_str, poor_user_rate_str, error_rate_str, p50, priority_rank. "
        f"Read these directly — do not recalculate."
        f"{context_block}"
    )
    return Content.model_validate({"role": "user", "parts": [{"text": text}]})


def _truncate_previous_context(value: str | None) -> str | None:
    if value is None:
        return None
    if len(value) > INTERACTIONS_OVERVIEW_PREVIOUS_CONTEXT_MAX_LEN:
        logger.warning(
            "previousContext truncated from %d to %d chars",
            len(value),
            INTERACTIONS_OVERVIEW_PREVIOUS_CONTEXT_MAX_LEN,
        )
        return value[:INTERACTIONS_OVERVIEW_PREVIOUS_CONTEXT_MAX_LEN]
    return value


# ── Main entry point ──────────────────────────────────────────────────────────


async def generate_interactions_overview(
    runner: Any,
    bearer_token: str,
    project_id: str,
    previous_context: str | None = None,
) -> InteractionsOverviewResponse:
    """Run the interactions overview pipeline and return a typed response.

    The user-facing summary is assembled by Python from pre-computed health_data
    (stored in session state by query_interaction_health). The LLM contributes only
    qualitative hypothesis text and trend notes — never numeric values in the summary.
    """
    session_id = str(uuid.uuid4())

    await runner.session_service.create_session(
        app_name=APP_NAME,
        user_id=USER_ID_INTERACTIONS_OVERVIEW,
        session_id=session_id,
        state={
            "bearer_token": bearer_token,
            "project_id": project_id,
        },
    )

    message = _build_interactions_overview_message(_truncate_previous_context(previous_context))

    async def _run() -> None:
        async for _ in runner.run_async(
            user_id=USER_ID_INTERACTIONS_OVERVIEW,
            session_id=session_id,
            new_message=message,
        ):
            pass

    try:
        try:
            await asyncio.wait_for(_run(), timeout=INTERACTIONS_OVERVIEW_TIMEOUT_SECONDS)
        except TimeoutError as error:
            logger.warning(
                "Interactions overview timed out, session_id=%s", session_id
            )
            raise InteractionsOverviewRunnerError(
                504, "Interactions overview timed out"
            ) from error

        session = await runner.session_service.get_session(
            app_name=APP_NAME,
            user_id=USER_ID_INTERACTIONS_OVERVIEW,
            session_id=session_id,
        )

        raw = session.state.get("interactions_overview_result") if session else None
        if not raw:
            logger.error(
                "interactions_overview_result missing from session state, session_id=%s",
                session_id,
            )
            raise InteractionsOverviewRunnerError(
                500,
                "Interactions overview result missing structured payload",
            )

        try:
            validated = InteractionOverviewOutputV1.model_validate(raw)
        except Exception:
            logger.exception(
                "Schema validation failed for interactions overview result, session_id=%s",
                session_id,
            )
            raise InteractionsOverviewRunnerError(
                500,
                "Interactions overview returned invalid structured payload",
            )

        # Build number-accurate summary from pre-computed health_data
        health_data: dict[str, dict] = (
            session.state.get("health_data") or {} if session else {}
        )
        if not health_data:
            logger.warning(
                "health_data missing from session state — summary will lack numeric context, session_id=%s",
                session_id,
            )

        assembled_summary = _assemble_summary(validated, health_data)
        sanitized_summary = sanitize_pii(assembled_summary)
        sanitized_context = sanitize_pii(validated.context)

        return InteractionsOverviewResponse(
            summary=sanitized_summary,
            context=sanitized_context,
            generatedAt=datetime.now(timezone.utc).isoformat(),
        )
    finally:
        try:
            await runner.session_service.delete_session(
                app_name=APP_NAME,
                user_id=USER_ID_INTERACTIONS_OVERVIEW,
                session_id=session_id,
            )
        except Exception:
            logger.warning(
                "Failed to delete ephemeral interactions overview session %s",
                session_id,
                exc_info=True,
            )
