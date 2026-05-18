"""Tests for pulse_ai.agents.privacy — with_privacy() instruction wrapper."""
from pulse_ai.agents.privacy import with_privacy


def _make_base(content: str):
    """Helper: returns a callable instruction that ignores ctx and returns content."""
    def fn(ctx=None):
        return content
    return fn


class TestWithPrivacyReturnsCallable:

    def test_returns_callable(self):
        wrapped = with_privacy(_make_base("base"), "cap")
        assert callable(wrapped)


class TestWithPrivacyFixedIdentityBlock:

    def test_result_contains_identity_section(self):
        result = with_privacy(_make_base("base"), "cap")(None)
        assert "IDENTITY:" in result

    def test_result_names_pulse_ai_as_product(self):
        result = with_privacy(_make_base("base"), "cap")(None)
        assert "Pulse AI" in result

    def test_result_forbids_internal_pipeline_names(self):
        result = with_privacy(_make_base("base"), "cap")(None)
        assert "EMAgent" in result


class TestWithPrivacyFixedPrivacyRules:

    def test_result_contains_implementation_privacy_section(self):
        result = with_privacy(_make_base("base"), "cap")(None)
        assert "IMPLEMENTATION PRIVACY:" in result

    def test_result_contains_model_secrecy_rule(self):
        result = with_privacy(_make_base("base"), "cap")(None)
        assert "Never confirm or deny which AI model" in result


class TestWithPrivacyCapabilityInstructions:

    def test_result_contains_capability_instructions(self):
        cap = "When asked what you can do, say: I can analyze performance."
        result = with_privacy(_make_base("base"), cap)(None)
        assert cap in result


class TestWithPrivacyBaseInstructionPassthrough:

    def test_result_contains_base_instruction_output(self):
        base_content = "You are the EM agent. CAPABILITIES: query interactions."
        result = with_privacy(_make_base(base_content), "cap")(None)
        assert base_content in result

    def test_ctx_is_forwarded_to_base_fn(self):
        received = []

        def fn(ctx=None):
            received.append(ctx)
            return "content"

        sentinel = object()
        with_privacy(fn, "cap")(sentinel)
        assert received == [sentinel]

    def test_works_with_string_instruction(self):
        result = with_privacy("static instruction", "cap")(None)
        assert "static instruction" in result
        assert "IDENTITY:" in result
