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
import logging
from typing import TYPE_CHECKING, Any, Optional

from google.adk.sessions import BaseSessionService

from pulse_ai.constants import (
    MAX_WINDOW_SAFETY_CAP,
    TOOL_AGE_THRESHOLD,
    TOKEN_BUDGET,
    _FIXED_OVERHEAD_ESTIMATE,
)
from pulse_ai.server.compaction_rules import compact_tool_response
from pulse_ai.server.token_estimator import estimate_tokens_for_event

if TYPE_CHECKING:
    from google.adk.events.event import Event
    from google.adk.sessions.base_session_service import (
        GetSessionConfig,
        ListSessionsResponse,
    )
    from google.adk.sessions.session import Session

_log = logging.getLogger(__name__)

# Effective per-request token budget after reserving space for system prompts
# and tool definitions (which are counted by the model but not in event history).
_EFFECTIVE_TOKEN_BUDGET = TOKEN_BUDGET - _FIXED_OVERHEAD_ESTIMATE


def _has_open_function_call(events: list) -> bool:
    """Return True if events contain a function_call with no matching function_response.

    Matches by id (Gemini newer ADK) with fallback to name (older ADK).
    Used to prevent _group_into_turns from splitting a call/response pair.
    """
    open_ids: set = set()
    for e in events:
        if not getattr(e, "content", None):
            continue
        for p in getattr(e.content, "parts", None) or []:
            fc = getattr(p, "function_call", None)
            if fc:
                key = getattr(fc, "id", None) or getattr(fc, "name", "?")
                open_ids.add(key)
            fr = getattr(p, "function_response", None)
            if fr:
                key = getattr(fr, "id", None) or getattr(fr, "name", "?")
                open_ids.discard(key)
    return bool(open_ids)


class CompactingSessionService(BaseSessionService):
    """Wraps any ADK SessionService to compact old tool responses on get_session.

    Subclasses ``BaseSessionService`` so ADK ``InvocationContext`` / Runner
    pydantic validation accepts this wrapper (``is_instance_of`` check).
    """

    def __init__(self, inner: BaseSessionService) -> None:
        self._inner = inner

    # ── Primary override ──────────────────────────────────────────────────────

    async def get_session(
        self,
        *,
        app_name: str,
        user_id: str,
        session_id: str,
        config: Optional["GetSessionConfig"] = None,
    ) -> Optional["Session"]:
        session = await self._inner.get_session(
            app_name=app_name,
            user_id=user_id,
            session_id=session_id,
            config=config,
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
        n_compacted = 0
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
                    n_compacted += 1

        # Step 2: Safety cap — keep first turn + last (CAP-1) turns
        if total_turns > MAX_WINDOW_SAFETY_CAP:
            turns = [turns[0]] + turns[-(MAX_WINDOW_SAFETY_CAP - 1):]

        # Step 3: Token budget — drop second-oldest turns until under budget.
        # Uses _EFFECTIVE_TOKEN_BUDGET (TOKEN_BUDGET minus system-prompt overhead)
        # so the cap is honest about the model's actual available window.
        # Turn at index 0 is always pinned (first user message).
        while len(turns) > 1:
            flat = [e for t in turns for e in t]
            total_tokens = sum(estimate_tokens_for_event(e) for e in flat)
            if total_tokens <= _EFFECTIVE_TOKEN_BUDGET:
                break
            # Drop the second-oldest turn (index 1); keep index 0 pinned
            turns = [turns[0]] + turns[2:]

        # Flatten and attach to session copy
        final_events = [e for t in turns for e in t]
        if n_compacted or len(turns) < total_turns:
            _log.info(
                "compaction: turns %d→%d, events %d→%d, tool_responses_compacted=%d",
                total_turns,
                len(turns),
                len(events),
                len(final_events),
                n_compacted,
            )
        session_copy = copy.copy(session)
        session_copy.events = final_events
        return session_copy

    @staticmethod
    def _group_into_turns(events: list) -> list[list]:
        """Group a flat event list into turns.

        A new turn begins with each user event, provided the current group has
        no pending (unmatched) function_call.  Holding the boundary open when a
        call is in-flight ensures function_call / function_response pairs are
        never split across turns — regardless of whether ADK authors the
        function_response event as the agent or as 'user'.
        """
        turns: list[list] = []
        current: list = []
        for event in events:
            if (
                getattr(event, "author", None) == "user"
                and current
                and not _has_open_function_call(current)
            ):
                turns.append(current)
                current = []
            current.append(event)
        if current:
            turns.append(current)
        return turns

    # ── Delegation ────────────────────────────────────────────────────────────

    async def create_session(
        self,
        *,
        app_name: str,
        user_id: str,
        state: Optional[dict[str, Any]] = None,
        session_id: Optional[str] = None,
    ) -> "Session":
        # Omit default None kwargs so mocks and inner services see the same call
        # shape as direct BaseSessionService callers.
        kwargs: dict[str, Any] = {"app_name": app_name, "user_id": user_id}
        if state is not None:
            kwargs["state"] = state
        if session_id is not None:
            kwargs["session_id"] = session_id
        return await self._inner.create_session(**kwargs)

    async def delete_session(
        self, *, app_name: str, user_id: str, session_id: str
    ) -> None:
        return await self._inner.delete_session(
            app_name=app_name, user_id=user_id, session_id=session_id
        )

    async def list_sessions(
        self, *, app_name: str, user_id: Optional[str] = None
    ) -> "ListSessionsResponse":
        return await self._inner.list_sessions(
            app_name=app_name, user_id=user_id
        )

    async def append_event(self, session: "Session", event: "Event") -> "Event":
        return await self._inner.append_event(session, event)

    def __getattr__(self, name: str) -> Any:
        """Delegate any unknown attribute or method to the inner service."""
        return getattr(self._inner, name)