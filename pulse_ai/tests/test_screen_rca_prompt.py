"""Tests for Screen RCA prompt correctness and output schema constraints.

Covers the 4 bug classes identified from production output:
1. Improving frustration metrics (negative delta) must not generate recommendations.
2. Neutral metrics (click_volume, tap_count) must not drive recommendations.
3. everythingGood / noDataAvailable must yield empty recommendations, not forced generic bullets.
4. executive_summary must not ask the LLM to calculate scope from volumes.

Also covers the ScreenRcaNarrativeV1 schema: min=0, max=3 recommendations.
"""

import pytest
from pydantic import ValidationError

from pulse_ai.agents.screen_rca.prompts import build_screen_rca_system_instruction
from pulse_ai.schemas.screen_rca_narrative_v1 import ScreenRcaNarrativeV1


# ──────────────────────────────────────────────────────────────
# Module-level helper
# ──────────────────────────────────────────────────────────────

def _prompt() -> str:
    return build_screen_rca_system_instruction()


# ──────────────────────────────────────────────────────────────
# 1. Schema constraints
# ──────────────────────────────────────────────────────────────

class TestScreenRcaSchema:
    """ScreenRcaNarrativeV1 must allow 0–3 recommendations."""

    def test_schema_allows_empty_recommendations(self):
        n = ScreenRcaNarrativeV1(version=1, executive_summary="Screen is healthy.", recommendations=[])
        assert n.recommendations == []

    def test_schema_allows_one_recommendation(self):
        n = ScreenRcaNarrativeV1(version=1, executive_summary="x", recommendations=["Audit dead clicks on Android."])
        assert len(n.recommendations) == 1

    def test_schema_allows_max_three_recommendations(self):
        n = ScreenRcaNarrativeV1(version=1, executive_summary="x", recommendations=["a", "b", "c"])
        assert len(n.recommendations) == 3

    def test_schema_rejects_more_than_three(self):
        with pytest.raises(ValidationError):
            ScreenRcaNarrativeV1(version=1, executive_summary="x", recommendations=["a", "b", "c", "d"])


# ──────────────────────────────────────────────────────────────
# 2. Metric directionality
# ──────────────────────────────────────────────────────────────

class TestScreenRcaPromptMetricDirectionality:
    """Prompt must distinguish directional frustration signals from neutral metrics."""

    def test_neutral_metrics_explicitly_nondirectional(self):
        prompt = _prompt()
        assert "non-directional" in prompt.lower() or "neutral" in prompt.lower()

    def test_neutral_metrics_are_context_only(self):
        prompt = _prompt()
        assert "context only" in prompt.lower()

    def test_frustration_signals_section_exists(self):
        prompt = _prompt()
        assert "frustration signal" in prompt.lower()

    def test_click_volume_and_tap_count_present(self):
        prompt = _prompt()
        assert "click_volume" in prompt
        assert "tap_count" in prompt


# ──────────────────────────────────────────────────────────────
# 3. Negative delta must not trigger recommendations
# ──────────────────────────────────────────────────────────────

class TestScreenRcaPromptNegativeDeltaNotActionable:
    """Prompt must prohibit flagging or recommending on improving (negative) deltas.

    Root cause of screenshot bug: dead_count was -44% (good) but LLM still
    wrote "Review the contributing factors that led to zero dead clicks..."
    """

    def test_only_positive_deltas_are_flagged(self):
        prompt = _prompt()
        assert any(phrase in prompt for phrase in [
            "Only flag or discuss these when their delta is positive",
            "only when their delta is positive",
            "positive delta is a degradation signal",
        ])

    def test_improving_metrics_excluded_from_recommendations(self):
        prompt = _prompt()
        prompt_lower = prompt.lower()
        assert (
            "do not recommend" in prompt_lower
            or "not recommend" in prompt_lower
            or "improving" in prompt_lower
        )

    def test_recommendations_grounded_in_frustration_signals_only(self):
        # Screenshot bug: "Examine if the lower click volumes..." — neutral metric drove a bullet.
        # Prompt must tie recommendations to frustration signals only.
        prompt = _prompt()
        prompt_lower = prompt.lower()
        assert (
            "frustration signal" in prompt_lower
            or "grounded in frustration" in prompt_lower
            or ("neutral" in prompt_lower and "recommendation" in prompt_lower)
        )


# ──────────────────────────────────────────────────────────────
# 4. Pre-analysis gate: no forced generic recommendations
# ──────────────────────────────────────────────────────────────

class TestScreenRcaPromptPreAnalysisGate:
    """everythingGood and noDataAvailable must yield recommendations: [], not generic bullets."""

    def test_everything_good_yields_empty_recommendations(self):
        prompt = _prompt()
        assert "recommendations: []" in prompt

    def test_no_data_available_yields_empty_recommendations(self):
        prompt = _prompt()
        assert "noDataAvailable" in prompt or "no_data_available" in prompt
        assert "recommendations: []" in prompt

    def test_no_forced_generic_recommendations(self):
        prompt = _prompt()
        assert "still output" not in prompt
        assert "3 minimal" not in prompt

    def test_no_filler_examples_in_gate(self):
        prompt = _prompt()
        assert "Continue monitoring" not in prompt
        assert "Verify data collection" not in prompt


# ──────────────────────────────────────────────────────────────
# 5. Recommendation quality rules
# ──────────────────────────────────────────────────────────────

class TestScreenRcaPromptRecommendationRules:
    """Recommendations must be verb-led, segment-named, and grounded in concrete actions."""

    def test_recommendations_are_verb_led(self):
        prompt = _prompt()
        assert "verb-led" in prompt.lower() or "verb led" in prompt.lower()

    def test_recommendations_must_name_segment_or_dimension(self):
        prompt = _prompt()
        prompt_lower = prompt.lower()
        assert "segment" in prompt_lower and "dimension" in prompt_lower

    def test_recommendations_no_vague_monitoring_without_slice(self):
        prompt = _prompt()
        prompt_lower = prompt.lower()
        assert "vague" in prompt_lower or "without naming" in prompt_lower or "do not" in prompt_lower

    def test_recommendations_require_concrete_action(self):
        prompt = _prompt()
        prompt_lower = prompt.lower()
        assert "concrete" in prompt_lower or "engineering" in prompt_lower


# ──────────────────────────────────────────────────────────────
# 6. Executive summary rules
# ──────────────────────────────────────────────────────────────

class TestScreenRcaPromptExecutiveSummary:
    """Executive summary must lead with concentration, not ask for volume-derived calculations."""

    def test_no_scope_calculation_instruction(self):
        # Original bug: "scope of impact when clear from volumes"
        prompt = _prompt()
        assert "scope of impact when clear from volumes" not in prompt

    def test_leads_with_frustration_concentration(self):
        prompt = _prompt()
        prompt_lower = prompt.lower()
        assert "concentrate" in prompt_lower or "lead with" in prompt_lower

    def test_do_not_invent_figures(self):
        prompt = _prompt()
        prompt_lower = prompt.lower()
        assert "do not calculate" in prompt_lower or "do not invent" in prompt_lower or "invent" in prompt_lower
