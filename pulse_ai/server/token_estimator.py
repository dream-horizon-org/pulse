"""Token estimation utilities for compaction budget calculations.

Uses a chars-per-token heuristic (len(text) / 4) standard for Gemini models.
Not exact — used only to enforce the 40K soft token budget in
CompactingSessionService, not for billing.
"""

from __future__ import annotations

import json
from typing import Any

from pulse_ai.constants import CHARS_PER_TOKEN, CHARS_PER_TOKEN_JSON


def estimate_tokens_for_text(text: str) -> int:
    """Estimate token count for a plain text string.

    Uses len(text) // CHARS_PER_TOKEN with a minimum of 1.
    """
    return max(1, len(text) // CHARS_PER_TOKEN)


def estimate_tokens_for_event(event: Any) -> int:
    """Estimate total token count for a single ADK event.

    Sums over all parts: text, function_call args, and function_response payload.
    Returns minimum 1 to ensure every event contributes to the budget.
    """
    content = getattr(event, "content", None)
    if not content:
        return 1

    parts = getattr(content, "parts", None) or []
    if not parts:
        return 1

    total = 0
    for part in parts:
        if part.text:
            total += estimate_tokens_for_text(part.text)

        fn_call = getattr(part, "function_call", None)
        if fn_call:
            name = getattr(fn_call, "name", "") or ""
            args = getattr(fn_call, "args", {}) or {}
            try:
                payload = name + json.dumps(args)
            except (TypeError, ValueError):
                payload = name
            total += max(1, len(payload) // CHARS_PER_TOKEN_JSON)

        fn_resp = getattr(part, "function_response", None)
        if fn_resp:
            response = getattr(fn_resp, "response", {}) or {}
            try:
                payload = json.dumps(response)
            except (TypeError, ValueError):
                payload = str(response)
            total += max(1, len(payload) // CHARS_PER_TOKEN_JSON)

    return max(1, total)
