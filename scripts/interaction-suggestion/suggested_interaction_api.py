"""Pulse API client for suggested-interaction mining jobs (stdlib only)."""

from __future__ import annotations

import json
import os
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Any


@dataclass(frozen=True)
class PulseApiConfig:
    base_url: str
    project_id: str
    auth_token: str
    token_type: str = "Bearer"

    @classmethod
    def from_env(cls) -> PulseApiConfig:
        base_url = os.environ.get("PULSE_API_BASE_URL", "").rstrip("/")
        project_id = os.environ.get("PULSE_PROJECT_ID", "")
        auth_token = os.environ.get("PULSE_AUTH_TOKEN", "")
        token_type = os.environ.get("PULSE_TOKEN_TYPE", "Bearer")
        if not base_url or not project_id or not auth_token:
            raise ValueError(
                "API mode requires PULSE_API_BASE_URL, PULSE_PROJECT_ID, and PULSE_AUTH_TOKEN "
                "(or pass --api-base-url, --project-id, --auth-token)."
            )
        return cls(base_url=base_url, project_id=project_id, auth_token=auth_token, token_type=token_type)


class PulseInteractionApiClient:
    def __init__(self, config: PulseApiConfig) -> None:
        self._config = config

    def _headers(self) -> dict[str, str]:
        return {
            "Authorization": f"{self._config.token_type} {self._config.auth_token}",
            "X-Project-ID": self._config.project_id,
            "Content-Type": "application/json",
            "Accept": "application/json",
        }

    def _request(
        self,
        method: str,
        path: str,
        *,
        body: dict[str, Any] | None = None,
        query: dict[str, str] | None = None,
    ) -> Any:
        url = f"{self._config.base_url}{path}"
        if query:
            params = "&".join(f"{k}={urllib.parse.quote(v, safe='')}" for k, v in query.items() if v)
            if params:
                url = f"{url}?{params}"
        data = None
        if body is not None:
            data = json.dumps(body).encode("utf-8")
        req = urllib.request.Request(url, data=data, headers=self._headers(), method=method)
        try:
            with urllib.request.urlopen(req) as resp:
                raw = resp.read().decode("utf-8")
                if not raw:
                    return None
                payload = json.loads(raw)
                if isinstance(payload, dict) and "data" in payload:
                    return payload["data"]
                return payload
        except urllib.error.HTTPError as exc:
            detail = exc.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"HTTP {exc.code} {method} {path}: {detail[:2000]}") from exc

    def get_interaction_configs(self) -> list[dict[str, Any]]:
        payload = self._request("GET", "/v1/interaction-configs")
        if isinstance(payload, list):
            return payload
        return []

    def get_suggestion_catalog(self, status: str | None = "ALL") -> list[dict[str, Any]]:
        """Fetch suggestions; use status=ALL for mining dedup (all statuses, no UI filter)."""
        query = {"status": status} if status else None
        payload = self._request("GET", "/v1/interactions/suggestions", query=query)
        if not isinstance(payload, dict):
            return []
        return list(payload.get("suggestions") or [])

    def post_suggestions(
        self,
        suggestions: list[dict[str, Any]],
        *,
        replace_pending: bool = True,
    ) -> dict[str, Any]:
        body = {
            "replacePending": replace_pending,
            "suggestions": suggestions,
        }
        payload = self._request("POST", "/v1/interactions/suggestions", body=body)
        return payload if isinstance(payload, dict) else {}


def _normalize_props(props: list[dict[str, Any]] | None) -> list[tuple[str, str, str]]:
    out: list[tuple[str, str, str]] = []
    for prop in props or []:
        name = str(prop.get("name", ""))
        value = str(prop.get("value", ""))
        operator = str(prop.get("operator") or "EQUALS").upper()
        out.append((name, value, operator))
    return out


def events_signature(events: list[dict[str, Any]]) -> tuple[tuple[str, tuple[tuple[str, str, str], ...]], ...]:
    sig: list[tuple[str, tuple[tuple[str, str, str], ...]]] = []
    for event in events:
        name = str(event.get("name", ""))
        props = _normalize_props(event.get("props"))
        sig.append((name, tuple(props)))
    return tuple(sig)


def pattern_signature(pattern: list[str]) -> tuple[tuple[str, tuple[()]], ...]:
    return tuple((str(name), ()) for name in pattern)


def is_duplicate_event_sequence(
    candidate_events: list[dict[str, Any]] | list[str],
    existing_events: list[dict[str, Any]],
) -> bool:
    if isinstance(candidate_events, list) and candidate_events and isinstance(candidate_events[0], str):
        cand_sig = pattern_signature(candidate_events)  # type: ignore[arg-type]
    else:
        cand_sig = events_signature(candidate_events)  # type: ignore[arg-type]
    return events_signature(existing_events) == cand_sig


def filter_patterns_against_existing(
    patterns: list[dict[str, Any]],
    *,
    interactions: list[dict[str, Any]],
    catalog_suggestions: list[dict[str, Any]],
) -> tuple[list[dict[str, Any]], dict[str, int]]:
    excluded_interactions = 0
    excluded_suggestions = 0
    kept: list[dict[str, Any]] = []

    interaction_events = [
        list(i.get("events") or [])
        for i in interactions
        if isinstance(i.get("events"), list) and i.get("events")
    ]
    suggestion_events = [
        list(s.get("events") or [])
        for s in catalog_suggestions
        if isinstance(s.get("events"), list) and s.get("events")
    ]

    for pattern in patterns:
        candidate_events = pattern_to_api_events(pattern)
        dup_interaction = any(
            is_duplicate_event_sequence(candidate_events, existing)
            for existing in interaction_events
        )
        dup_suggestion = any(
            is_duplicate_event_sequence(candidate_events, existing)
            for existing in suggestion_events
        )
        if dup_interaction or dup_suggestion:
            if dup_interaction:
                excluded_interactions += 1
            if dup_suggestion:
                excluded_suggestions += 1
            continue
        kept.append(pattern)

    stats = {
        "excluded_interactions": excluded_interactions,
        "excluded_suggestions": excluded_suggestions,
        "kept": len(kept),
    }
    return kept, stats


def pattern_to_api_events(pattern: dict[str, Any]) -> list[dict[str, Any]]:
    names = pattern.get("pattern") or []
    return [
        {"name": str(name), "props": [], "isBlacklisted": False}
        for name in names
    ]


def pattern_to_api_suggestion(pattern: dict[str, Any]) -> dict[str, Any]:
    edges = pattern.get("edges") or []
    return {
        "events": pattern_to_api_events(pattern),
        "totalOccurrences": int(pattern.get("total_occurrences", 0)),
        "uniqueSessions": int(pattern.get("unique_sessions", 0)),
        "sessionPct": float(pattern.get("session_pct", 0.0)),
        "meanSpanS": float(pattern.get("mean_span_s", 0.0)),
        "medianSpanS": float(pattern.get("median_span_s", 0.0)),
        "p95SpanS": float(pattern.get("p95_span_s", 0.0)),
        "cv": float(pattern.get("cv", 0.0)),
        "edges": edges,
    }
