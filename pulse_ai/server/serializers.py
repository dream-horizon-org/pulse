"""
Event serialization and streaming helpers.

Converts ADK agent events into JSON-friendly dicts and SSE-formatted strings.
Only events authored by the Report agent are surfaced to the frontend;
intermediate pipeline agents (Planner, Executor, Summary) are filtered out.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

from pulse_ai.constants import DEFAULT_TITLE, REPORT_AGENT_NAME, TITLE_MAX_LENGTH


class DeltaTracker:
    """Tracks cumulative text from the LLM and yields only new deltas.

    ADK's runner.run_async() emits cumulative text events — each event
    contains the full text so far, not just the new portion. This class
    deduplicates by remembering what has already been sent.
    """

    def __init__(self) -> None:
        self._seen = ""

    def push(self, text: str) -> str:
        if text.startswith(self._seen):
            new = text[len(self._seen):]
        else:
            new = text
        if new:
            self._seen += new
        return new

    @property
    def full_text(self) -> str:
        return self._seen

    def reset(self) -> None:
        self._seen = ""


def extract_content_blocks(parts: Any, author: str) -> tuple[list[str], list[dict]]:
    """Pull text segments and structured content blocks from event parts.

    Returns (texts, blocks).  Text is extracted for both user and Report-agent
    events.  Content blocks (charts/tables) are only extracted from Report-agent
    events.  Intermediate pipeline agents are fully skipped.
    """
    is_report = author == REPORT_AGENT_NAME
    is_user = author == "user"
    if not is_report and not is_user:
        return [], []

    texts: list[str] = []
    blocks: list[dict] = []
    for part in parts:
        if part.text:
            texts.append(part.text)
        if is_report and part.function_response:
            resp = part.function_response.response
            if isinstance(resp, dict):
                if "chart" in resp:
                    blocks.append({"block_type": "chart", **resp["chart"]})
                if "table" in resp:
                    blocks.append({"block_type": "table", **resp["table"]})
    return texts, blocks


def extract_title(events: Any) -> str:
    """Derive a conversation title from the first user message."""
    for ev in (events or []):
        if ev.author == "user" and ev.content and ev.content.parts:
            for part in ev.content.parts:
                if part.text:
                    return part.text[:TITLE_MAX_LENGTH]
    return DEFAULT_TITLE


# ── Session history serialization ────────────────────────────────────────────


@dataclass
class _AgentTurn:
    """Accumulates Report-agent output across multiple events into one message."""

    text: str = ""
    charts: list[dict] = field(default_factory=list)
    tables: list[dict] = field(default_factory=list)

    def is_empty(self) -> bool:
        return not self.text and not self.charts and not self.tables

    def to_message(self) -> dict[str, Any]:
        return {
            "role": "model",
            "text": self.text,
            "charts": list(self.charts),
            "tables": list(self.tables),
        }


def events_to_messages(events: list[Any] | None) -> list[dict[str, Any]]:
    """Collapse ADK events into a flat list of chat messages.

    Uses delta tracking to deduplicate cumulative partial text events
    emitted by ADK.  Only Report-agent text and tool outputs are included.
    """
    messages: list[dict[str, Any]] = []
    turn = _AgentTurn()

    def flush() -> None:
        nonlocal turn
        if not turn.is_empty():
            messages.append(turn.to_message())
            turn = _AgentTurn()

    for ev in (events or []):
        if not ev.content or not ev.content.parts:
            continue

        texts, blocks = extract_content_blocks(ev.content.parts, ev.author)

        if ev.author == "user":
            flush()
            joined = "".join(texts)
            if joined:
                messages.append({
                    "role": "user",
                    "text": joined,
                    "charts": [],
                    "tables": [],
                })
            continue

        if ev.author != REPORT_AGENT_NAME:
            continue

        text = "".join(texts)
        if text:
            if text.startswith(turn.text):
                turn.text = text
            elif not turn.text.startswith(text):
                turn.text += text

        for b in blocks:
            btype = b.get("block_type")
            cleaned = {k: v for k, v in b.items() if k != "block_type"}
            if btype == "chart":
                turn.charts.append(cleaned)
            elif btype == "table":
                turn.tables.append(cleaned)

    flush()
    return messages
