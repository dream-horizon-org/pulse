"""Tests for screen RCA narrative agent and schema."""

import pytest


class TestScreenRcaNarrativeSchema:
    def test_valid_narrative(self):
        from pulse_ai.schemas.screen_rca_narrative_v1 import ScreenRcaNarrativeV1

        n = ScreenRcaNarrativeV1(
            version=1,
            executive_summary="One. Two. Three.",
            recommendations=["a", "b", "c"],
        )
        assert n.version == 1
        assert len(n.recommendations) == 3

    def test_recommendations_min_length(self):
        from pulse_ai.schemas.screen_rca_narrative_v1 import ScreenRcaNarrativeV1
        from pydantic import ValidationError

        with pytest.raises(ValidationError):
            ScreenRcaNarrativeV1(
                executive_summary="x",
                recommendations=["only", "two"],
            )


class TestScreenRcaAgentWiring:
    def test_agent_exists(self):
        from pulse_ai.agents.screen_rca import screen_rca_narrative_agent

        assert screen_rca_narrative_agent is not None

    def test_agent_output_key(self):
        from pulse_ai.agents.screen_rca import screen_rca_narrative_agent

        assert screen_rca_narrative_agent.output_key == "screen_rca_narrative"

    def test_agent_output_schema(self):
        from pulse_ai.agents.screen_rca import screen_rca_narrative_agent
        from pulse_ai.schemas.screen_rca_narrative_v1 import ScreenRcaNarrativeV1

        assert screen_rca_narrative_agent.output_schema is ScreenRcaNarrativeV1

    def test_agent_no_tools(self):
        from pulse_ai.agents.screen_rca import screen_rca_narrative_agent

        assert screen_rca_narrative_agent.tools is None or len(screen_rca_narrative_agent.tools) == 0


class TestScreenRcaPromptContent:
    def test_prompt_mentions_frustration_metrics(self):
        from pulse_ai.agents.screen_rca.prompts import build_screen_rca_system_instruction

        text = build_screen_rca_system_instruction(None)
        assert "bad_frustration" in text
        assert "rage_count" in text
        assert "dead_count" in text

    def test_prompt_forbids_session_evidence(self):
        from pulse_ai.agents.screen_rca.prompts import build_screen_rca_system_instruction

        text = build_screen_rca_system_instruction(None)
        assert "session" in text.lower()


class TestScreenRcaUserMessage:
    def test_build_includes_payload(self):
        from pulse_ai.schemas import RootCausePayloadSchema
        from pulse_ai.server.screen_rca_runner import _build_screen_rca_user_message

        payload = RootCausePayloadSchema.model_validate({
            "baseline": {"click_volume": 100},
            "segments": [],
        })
        msg = _build_screen_rca_user_message(
            "Home",
            payload,
            "2026-01-01T00:00:00Z",
            "2026-01-02T00:00:00Z",
            None,
            None,
        )
        assert "Home" in msg
        assert "click_volume" in msg
        assert "2026-01-01T00:00:00Z" in msg


class TestScreenRcaReportRequestSchema:
    def test_request_model_fields(self):
        from pulse_ai.server.schemas import ScreenRcaReportRequest

        r = ScreenRcaReportRequest(
            screenName="Checkout",
            rootCausePayload={"baseline": {}, "segments": []},
            start="2026-01-01T00:00:00Z",
            end="2026-01-08T00:00:00Z",
        )
        assert r.screenName == "Checkout"
        assert r.start is not None
