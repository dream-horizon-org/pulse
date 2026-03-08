import json

from google.genai import types

CORE_PERSONAS = {"Product Analytics", "Engineering Manager", "Designer"}
DEPENDENT_PERSONAS = {"Customer Success", "Business Leaders"}
ALL_PERSONAS = CORE_PERSONAS | DEPENDENT_PERSONAS


async def set_routing_flags(callback_context):
    """Parse the Planner's structured JSON output and set routing flags in state."""
    raw = callback_context.state.get("plan", "{}")
    try:
        plan = json.loads(raw) if isinstance(raw, str) else raw
    except (json.JSONDecodeError, TypeError):
        plan = {}

    selected = set(plan.get("selected_personas", []))

    callback_context.state["intent_clear"] = plan.get("intent_clear", True)
    callback_context.state["selected_personas"] = list(selected)
    callback_context.state["needs_analysis"] = bool(selected & ALL_PERSONAS)
    callback_context.state["needs_summary"] = bool(selected & DEPENDENT_PERSONAS)
    callback_context.state["clarification"] = plan.get("clarification_needed")
    return None


async def gate_on_clear_intent(callback_context):
    """Skip this agent if the Planner flagged intent as unclear."""
    if not callback_context.state.get("intent_clear", True):
        msg = callback_context.state.get(
            "clarification", "Could you clarify your question?"
        )
        return types.Content(role="model", parts=[types.Part(text=msg)])
    return None


async def gate_summary(callback_context):
    """Skip Summary if no dependent personas were selected."""
    if not callback_context.state.get("needs_summary", True):
        callback_context.state["summary"] = "(Summary skipped — core personas only.)"
        return types.Content(
            role="model",
            parts=[types.Part(text="(Summary skipped — core personas only.)")],
        )
    return None


def gate_persona(persona_name: str):
    """Factory: returns a callback that skips this agent if the persona wasn't selected."""

    _OUTPUT_KEY_MAP = {
        "Product Analytics": "product_analytics_result",
        "Engineering Manager": "engineering_manager_result",
        "Designer": "designer_result",
        "Customer Success": "customer_success_result",
        "Business Leaders": "business_leaders_result",
    }

    async def _callback(callback_context):
        selected = callback_context.state.get("selected_personas", [])
        if persona_name not in selected:
            output_key = _OUTPUT_KEY_MAP.get(persona_name)
            if output_key:
                callback_context.state[output_key] = f"({persona_name} analysis skipped — not selected.)"
            return types.Content(
                role="model",
                parts=[types.Part(text=f"({persona_name} analysis skipped.)")],
            )
        return None

    _callback.__name__ = f"gate_{persona_name.lower().replace(' ', '_')}"
    return _callback
