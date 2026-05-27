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


def _neutral_metrics_block(prompt: str) -> str:
    """Bullet list under ### Neutral metrics only (not later sections)."""
    start = prompt.lower().find("### neutral metric")
    assert start >= 0
    end = prompt.find("## Segments", start)
    assert end > start
    return prompt[start:end]


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

    def test_bad_frustration_percentage_is_primary_frustration_metric(self):
        prompt = _prompt()
        frustration_idx = prompt.lower().find("### frustration")
        neutral_idx = prompt.lower().find("### neutral")
        frustration_block = prompt[frustration_idx:neutral_idx]
        assert "bad_frustration_percentage" in frustration_block
        assert any(
            phrase in frustration_block.lower()
            for phrase in [
                "primary severity metric",
                "only frustration metric whose delta",
                "lead every summary and recommendation",
            ]
        )
        assert frustration_idx < neutral_idx

    def test_bad_frustration_equals_dead_plus_rage_mutually_exclusive(self):
        prompt = _prompt()
        frustration_idx = prompt.lower().find("### frustration")
        neutral_idx = prompt.lower().find("### neutral")
        block = prompt[frustration_idx:neutral_idx].lower()
        assert "composition" in block
        assert "dead_count + rage_count" in block
        assert "never both" in block or "mutually exclusive" in block
        assert "double-count" not in block

    def test_frustration_metrics_not_listed_under_neutral_bullets(self):
        prompt = _prompt()
        neutral_block = _neutral_metrics_block(prompt)
        assert "click_volume" in neutral_block
        assert "tap_count" in neutral_block
        assert neutral_block.count("rage_count") == 0
        assert neutral_block.count("dead_count") == 0
        assert "bad_frustration_percentage" not in neutral_block
        assert neutral_block.count("bad_frustration") == 0


# ──────────────────────────────────────────────────────────────
# 3. Negative delta must not trigger recommendations
# ──────────────────────────────────────────────────────────────

class TestScreenRcaPromptNegativeDeltaNotActionable:
    """Prompt must prohibit flagging or recommending on improving (negative) deltas.

    Root cause of screenshot bug: dead_count was -44% (good) but LLM still
    wrote "Review the contributing factors that led to zero dead clicks..."
    """

    def test_only_bad_frustration_percentage_delta_drives_baseline_worsening(self):
        prompt = _prompt()
        prompt_lower = prompt.lower()
        assert "subset math" in prompt_lower or "subset" in prompt_lower
        assert "bad_frustration_percentage" in prompt
        assert any(
            phrase in prompt_lower
            for phrase in [
                "only frustration metric whose delta",
                "reliably means",
                "lead every summary and recommendation on this metric",
            ]
        )

    def test_count_deltas_vs_baseline_not_recommendation_triggers(self):
        prompt = _prompt()
        prompt_lower = prompt.lower()
        assert "never recommend" in prompt_lower
        assert "rage_count" in prompt
        assert any(
            phrase in prompt_lower
            for phrase in [
                "deltas vs baseline are ≤ 0",
                "deltas vs baseline are <= 0",
                "almost always",
            ]
        )

    def test_improving_metrics_excluded_from_recommendations(self):
        prompt = _prompt()
        prompt_lower = prompt.lower()
        assert (
            "do not recommend" in prompt_lower
            or "not recommend" in prompt_lower
            or "improving" in prompt_lower
        )

    def test_recommendations_grounded_in_bad_frustration_rate_only(self):
        # Screenshot bug: "Examine if the lower click volumes..." — neutral metric drove a bullet.
        prompt = _prompt()
        prompt_lower = prompt.lower()
        assert "positive `bad_frustration_percentage` delta" in prompt_lower or (
            "bad_frustration_percentage" in prompt_lower
            and "mandatory" in prompt_lower
            and "recommendation" in prompt_lower
        )
        assert "neutral" in prompt_lower and "recommendation" in prompt_lower


# ──────────────────────────────────────────────────────────────
# 4. Pre-analysis gate: no forced generic recommendations
# ──────────────────────────────────────────────────────────────

class TestScreenRcaPromptPreAnalysisGate:
    """Input everythingGood / noDataAvailable must yield recommendations: [], not generic bullets."""

    def test_everything_good_yields_empty_recommendations(self):
        prompt = _prompt()
        assert "recommendations: []" in prompt
        assert "everythingGood" in prompt

    def test_no_data_available_yields_empty_recommendations(self):
        prompt = _prompt()
        assert "noDataAvailable" in prompt
        assert "recommendations: []" in prompt

    def test_gate_uses_input_flags_not_output_schema_fields(self):
        prompt = _prompt()
        assert "RootCausePayload" in prompt
        assert "do not emit extra output fields" in prompt.lower()
        assert "Set `everything_good: true`" not in prompt
        assert "Set `no_data_available: true`" not in prompt

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

    def test_leads_with_bad_frustration_percentage(self):
        prompt = _prompt()
        prompt_lower = prompt.lower()
        assert "lead with" in prompt_lower
        assert "bad_frustration_percentage" in prompt_lower

    def test_do_not_invent_figures(self):
        prompt = _prompt()
        prompt_lower = prompt.lower()
        assert "do not calculate" in prompt_lower or "do not invent" in prompt_lower or "invent" in prompt_lower


# ──────────────────────────────────────────────────────────────
# 7. Segment list and server gate alignment
# ──────────────────────────────────────────────────────────────

class TestScreenRcaPromptSegmentTrust:
    """Prompt must trust server segment order and rate gate."""

    def test_trust_server_segment_list(self):
        prompt = _prompt()
        prompt_lower = prompt.lower()
        assert "do not drop" in prompt_lower or "trust the server" in prompt_lower

    def test_segment_label_citation(self):
        prompt = _prompt()
        assert "label" in prompt.lower()

    def test_bad_frustration_percentage_rate_gate(self):
        prompt = _prompt()
        assert "bad_frustration_percentage" in prompt
        assert "strictly greater" in prompt.lower() or "baseline rate" in prompt.lower()

    def test_negative_count_deltas_expected_for_listed_segments(self):
        prompt = _prompt()
        prompt_lower = prompt.lower()
        assert "ignore those count deltas" in prompt_lower or "negative" in prompt_lower
        assert "rage_count" in prompt
