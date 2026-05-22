"""
Build an event-definition catalog CSV for a Pulse project by reading
``otel.otel_logs`` rows where ``PulseType='custom_event'``.

Output columns match the event_definitions template:
    event_name, event_description, category,
    attribute_name, attribute_description, attribute_type, attribute_required

Schema is inferred from telemetry; Gemini fills the description/category fields
that ClickHouse cannot supply. Pass ``--no-llm`` to skip enrichment.

Usage:
    python -m pulse_ai.scripts.build_event_catalog \\
        --project-id default-project \\
        --lookback-days 30 \\
        --max-rows 1000000 \\
        --output event_catalog.csv
"""
from __future__ import annotations

import argparse
import asyncio
import csv
import json
import logging
import os
import sys
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Iterable

try:
    from dotenv import load_dotenv
    # Load pulse_ai/.env (parent of this scripts/ folder) so CLICKHOUSE_* and
    # GOOGLE_API_KEY are picked up automatically. Existing env vars win.
    load_dotenv(Path(__file__).resolve().parent.parent / ".env", override=False)
except ImportError:
    pass

logger = logging.getLogger("build_event_catalog")

CSV_HEADER = [
    "event_name",
    "event_description",
    "category",
    "attribute_name",
    "attribute_description",
    "attribute_type",
    "attribute_required",
]

SYSTEM_KEY_PREFIXES = (
    "pulse.", "session.", "screen.", "app.", "device.", "network.",
    "geo.", "user.", "os.", "rum.", "click.", "service.", "telemetry.",
    "host.", "process.", "trace.", "span.",
)
SYSTEM_KEY_EXACT = {"event.name", "installation.id"}

REQUIRED_THRESHOLD = 0.95
LLM_CONCURRENCY = 10


# ───────────────────────────── ClickHouse ──────────────────────────────

def get_ch_client():
    """Create ClickHouse client from environment variables."""
    import clickhouse_connect

    host = os.getenv("CLICKHOUSE_HOST", "localhost")
    host = host.replace("https://", "").replace("http://", "").rstrip("/")

    return clickhouse_connect.get_client(
        host=host,
        port=int(os.getenv("CLICKHOUSE_PORT", "8123")),
        username=os.getenv("CLICKHOUSE_USER", "default"),
        password=os.getenv("CLICKHOUSE_PASSWORD", ""),
        database=os.getenv("CLICKHOUSE_DATABASE", "otel"),
    )


def stream_custom_events(
    client, project_id: str, lookback_days: int, max_rows: int
) -> Iterable[tuple[str, dict[str, str]]]:
    """Yield (event_name, log_attributes_dict) one row at a time."""
    query = """
        SELECT EventName, LogAttributes
        FROM otel.otel_logs
        WHERE ProjectId = {pid:String}
          AND PulseType = 'custom_event'
          AND Timestamp >= now() - toIntervalDay({days:UInt32})
          AND EventName != ''
        LIMIT {limit:UInt64}
    """
    params = {"pid": project_id, "days": lookback_days, "limit": max_rows}
    with client.query_rows_stream(query, parameters=params) as stream:
        for row in stream:
            yield row[0], (row[1] or {})


# ─────────────────────────── Schema aggregation ────────────────────────

@dataclass
class AttrStats:
    count: int = 0
    type_votes: dict[str, int] = field(default_factory=lambda: defaultdict(int))
    sample: str = ""


@dataclass
class EventStats:
    total: int = 0
    attrs: dict[str, AttrStats] = field(default_factory=dict)


def _is_user_attr(key: str) -> bool:
    if key in SYSTEM_KEY_EXACT:
        return False
    return not any(key.startswith(p) for p in SYSTEM_KEY_PREFIXES)


def _infer_type(value: str) -> str:
    if value is None or value == "":
        return "string"
    low = value.strip().lower()
    if low in ("true", "false"):
        return "bool"
    try:
        int(value)
        return "int"
    except ValueError:
        pass
    try:
        float(value)
        return "double"
    except ValueError:
        pass
    return "string"


def aggregate(rows: Iterable[tuple[str, dict[str, str]]]) -> dict[str, EventStats]:
    stats: dict[str, EventStats] = defaultdict(EventStats)
    for event_name, attrs in rows:
        es = stats[event_name]
        es.total += 1
        for k, v in attrs.items():
            if not _is_user_attr(k):
                continue
            a = es.attrs.get(k)
            if a is None:
                a = AttrStats()
                es.attrs[k] = a
            a.count += 1
            a.type_votes[_infer_type(v)] += 1
            if not a.sample and v:
                a.sample = v[:80]
    return stats


def _winning_type(votes: dict[str, int]) -> str:
    if not votes:
        return "string"
    return max(votes.items(), key=lambda kv: (kv[1], kv[0] != "string"))[0]


# ─────────────────────────────── LLM ───────────────────────────────────

async def enrich_with_gemini(
    stats: dict[str, EventStats], model: str
) -> dict[str, dict[str, Any]]:
    """Return {event_name: {event_description, category, attributes: {name: desc}}}."""
    try:
        from google import genai
    except ImportError:
        logger.warning("google.genai not installed; skipping LLM enrichment")
        return {}

    api_key = os.getenv("GOOGLE_API_KEY") or os.getenv("GEMINI_API_KEY")
    if not api_key:
        logger.warning("GOOGLE_API_KEY not set; skipping LLM enrichment")
        return {}

    client = genai.Client(api_key=api_key)
    sem = asyncio.Semaphore(LLM_CONCURRENCY)
    results: dict[str, dict[str, Any]] = {}

    async def one(event_name: str, es: EventStats) -> None:
        async with sem:
            attr_lines = [
                f"- {name} ({_winning_type(a.type_votes)})"
                + (f", e.g. {a.sample!r}" if a.sample else "")
                for name, a in sorted(es.attrs.items())
            ] or ["(no user-defined attributes)"]
            prompt = (
                "You are documenting a mobile/web analytics event.\n"
                f"event_name: {event_name}\n"
                "attributes:\n" + "\n".join(attr_lines) + "\n\n"
                "Return ONLY JSON with this shape (no markdown, no commentary):\n"
                '{"event_description":"<one short sentence>",'
                '"category":"<one lowercase word: commerce|auth|navigation|engagement|content|onboarding|search|notification|other>",'
                '"attributes":{"<attr_name>":"<one short sentence>"}}'
            )
            try:
                resp = await asyncio.to_thread(
                    client.models.generate_content,
                    model=model,
                    contents=prompt,
                )
                text = (resp.text or "").strip()
                if text.startswith("```"):
                    text = text.strip("`")
                    if text.lower().startswith("json"):
                        text = text[4:]
                results[event_name] = json.loads(text)
            except Exception as e:
                logger.warning("LLM enrichment failed for %s: %s", event_name, e)

    await asyncio.gather(*(one(n, s) for n, s in stats.items()))
    return results


# ─────────────────────────────── CSV write ─────────────────────────────

def write_csv(
    stats: dict[str, EventStats],
    enrichment: dict[str, dict[str, Any]],
    output_path: str,
) -> int:
    rows_written = 0
    with open(output_path, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=CSV_HEADER)
        w.writeheader()
        for event_name in sorted(stats):
            es = stats[event_name]
            enr = enrichment.get(event_name, {})
            ev_desc = enr.get("event_description", "") or ""
            category = enr.get("category", "") or ""
            attr_desc_map = enr.get("attributes", {}) or {}

            if not es.attrs:
                w.writerow({
                    "event_name": event_name,
                    "event_description": ev_desc,
                    "category": category,
                    "attribute_name": "",
                    "attribute_description": "",
                    "attribute_type": "",
                    "attribute_required": "",
                })
                rows_written += 1
                continue

            for attr_name in sorted(es.attrs):
                a = es.attrs[attr_name]
                required = (a.count / es.total) >= REQUIRED_THRESHOLD if es.total else False
                w.writerow({
                    "event_name": event_name,
                    "event_description": ev_desc,
                    "category": category,
                    "attribute_name": attr_name,
                    "attribute_description": attr_desc_map.get(attr_name, "") or "",
                    "attribute_type": _winning_type(a.type_votes),
                    "attribute_required": "true" if required else "false",
                })
                rows_written += 1
    return rows_written


# ─────────────────────────────── main ──────────────────────────────────

def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--project-id", required=True)
    p.add_argument("--lookback-days", type=int, default=30)
    p.add_argument("--max-rows", type=int, default=1_000_000)
    p.add_argument("--output", required=True)
    p.add_argument("--no-llm", action="store_true", help="Skip Gemini enrichment")
    p.add_argument(
        "--model",
        default=os.getenv("AGENT_MODEL", "gemini-2.5-flash"),
        help="Gemini model id (default: gemini-2.5-flash)",
    )
    p.add_argument("-v", "--verbose", action="store_true")
    return p.parse_args()


def main() -> int:
    args = parse_args()
    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(asctime)s %(levelname)s %(message)s",
    )

    logger.info(
        "Querying ClickHouse: project=%s lookback=%dd max_rows=%d",
        args.project_id, args.lookback_days, args.max_rows,
    )
    client = get_ch_client()
    try:
        rows = stream_custom_events(
            client, args.project_id, args.lookback_days, args.max_rows
        )
        stats = aggregate(rows)
    finally:
        client.close()

    if not stats:
        logger.error("No custom_event rows found for project %s", args.project_id)
        return 1

    logger.info(
        "Aggregated %d distinct events across %d rows",
        len(stats), sum(s.total for s in stats.values()),
    )

    enrichment: dict[str, dict[str, Any]] = {}
    if not args.no_llm:
        logger.info("Enriching with %s …", args.model)
        enrichment = asyncio.run(enrich_with_gemini(stats, args.model))
        logger.info("LLM enriched %d/%d events", len(enrichment), len(stats))

    rows_written = write_csv(stats, enrichment, args.output)
    logger.info("Wrote %d CSV rows to %s", rows_written, args.output)
    return 0


if __name__ == "__main__":
    sys.exit(main())
