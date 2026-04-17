"""CompactingSessionService — wraps an ADK SessionService to compact old tool
responses before they reach the LLM.

Design:
  - get_session() returns a deep-copied session with compacted events.
    The original session stored in the inner service is never mutated.
  - All other methods (create_session, delete_session, list_sessions, etc.)
    delegate directly to the inner service.
  - __getattr__ ensures unknown future ADK methods fall through to inner.

Compaction algorithm (per get_session call):
  1. Group events into turns (a new turn begins with each "user" event).
  2. For turns with age >= TOOL_AGE_THRESHOLD, replace function_response
     payloads with structured summaries via compaction_rules.
  3. Apply MAX_WINDOW_SAFETY_CAP: drop oldest turns (except the first) until
     turn count is within the cap.
  4. Apply TOKEN_BUDGET: estimate total tokens; drop second-oldest turns
     (keeping first pinned) until under budget.
"""

from __future__ import annotations

import copy
from typing import Any

from pulse_ai.constants import (
    MAX_WINDOW_SAFETY_CAP,
    TOOL_AGE_THRESHOLD,
    TOKEN_BUDGET,
)
from pulse_ai.server.compaction_rules import compact_tool_response
from pulse_ai.server.token_estimator import estimate_tokens_for_event


class CompactingSessionService:
    """Wraps any ADK SessionService to compact old tool responses on get_session."""

    def __init__(self, inner: Any) -> None:
        self._inner = inner

    # ── Primary override ──────────────────────────────────────────────────────

    async def get_session(
        self,
        *,
        app_name: str,
        user_id: str,
        session_id: str,
    ) -> Any:
        session = await self._inner.get_session(
            app_name=app_name,
            user_id=user_id,
            session_id=session_id,
        )
        if session is None:
            return None
        return self._apply_compaction(session)

    # ── Compaction logic ──────────────────────────────────────────────────────

    def _apply_compaction(self, session: Any) -> Any:
        events = list(getattr(session, "events", None) or [])
        if not events:
            return session

        # Deep copy: original is never mutated
        compacted_events = copy.deepcopy(events)

        # Group into turns: each turn starts at a "user" event
        turns = self._group_into_turns(compacted_events)
        total_turns = len(turns)

        # Step 1: Compact tool responses for turns older than TOOL_AGE_THRESHOLD
        for i, turn_events in enumerate(turns):
            turn_age = total_turns - 1 - i  # 0 = current turn, grows toward oldest
            if turn_age < TOOL_AGE_THRESHOLD:
                continue
            for event in turn_events:
                if not event.content:
                    continue
                for part in event.content.parts or []:
                    fn_resp = getattr(part, "function_response", None)
                    if fn_resp is None:
                        continue
                    tool_name = getattr(fn_resp, "name", "unknown_tool")
                    raw_response = getattr(fn_resp, "response", {})
                    summary = compact_tool_response(tool_name, raw_response)
                    fn_resp.response = {"compacted": True, "summary": summary}

        # Step 2: Safety cap — keep first turn + last (CAP-1) turns
        if total_turns > MAX_WINDOW_SAFETY_CAP:
            turns = [turns[0]] + turns[-(MAX_WINDOW_SAFETY_CAP - 1):]

        # Step 3: Token budget — drop second-oldest turns until under budget
        # Turn at index 0 is always pinned (first user message).
        while len(turns) > 1:
            flat = [e for t in turns for e in t]
            total_tokens = sum(estimate_tokens_for_event(e) for e in flat)
            if total_tokens <= TOKEN_BUDGET:
                break
            # Drop the second-oldest turn (index 1); keep index 0 pinned
            turns = [turns[0]] + turns[2:]

        # Flatten and attach to session copy
        final_events = [e for t in turns for e in t]
        session_copy = copy.copy(session)
        session_copy.events = final_events
        return session_copy

    @staticmethod
    def _group_into_turns(events: list) -> list[list]:
        """Group a flat event list into turns.

        A new turn begins with each user event. Events before the first user
        event (e.g. system events) are attached to the first turn.
        """
        turns: list[list] = []
        current: list = []
        for event in events:
            if getattr(event, "author", None) == "user" and current:
                turns.append(current)
                current = []
            current.append(event)
        if current:
            turns.append(current)
        return turns

    # ── Delegation ────────────────────────────────────────────────────────────

    async def create_session(self, **kwargs: Any) -> Any:
        return await self._inner.create_session(**kwargs)

    async def delete_session(self, **kwargs: Any) -> None:
        return await self._inner.delete_session(**kwargs)

    async def list_sessions(self, **kwargs: Any) -> Any:
        return await self._inner.list_sessions(**kwargs)

    async def append_event(self, **kwargs: Any) -> Any:
        return await self._inner.append_event(**kwargs)

    def __getattr__(self, name: str) -> Any:
        """Delegate any unknown attribute or method to the inner service."""
        return getattr(self._inner, name)
