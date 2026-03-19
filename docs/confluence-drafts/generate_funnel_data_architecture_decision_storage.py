#!/usr/bin/env python3
"""
Confluence storage XML for:
  Funnel & Journey Data Architecture Decision Document
  Page ID: 4775477367

Includes the finalized Spark + ClickHouse approach (formerly standalone page 4791042087).

Publish: MCP confluence_update_page(..., content_format='storage')
"""

from __future__ import annotations

import sys
from xml.sax.saxutils import escape


def p(text: str) -> str:
    return f"<p>{escape(text)}</p>"


def h1(t: str) -> str:
    return f"<h1>{escape(t)}</h1>"


def h2(t: str) -> str:
    return f"<h2>{escape(t)}</h2>"


def h3(t: str) -> str:
    return f"<h3>{escape(t)}</h3>"


def hr() -> str:
    return "<hr/>"


def pre(text: str) -> str:
    return f"<pre>{escape(text)}</pre>"


def table_html(headers: tuple[str, ...], rows: list[tuple[str, ...]]) -> str:
    hrow = "".join(f"<th>{escape(h)}</th>" for h in headers)
    body = ""
    for row in rows:
        body += "<tr>" + "".join(f"<td>{escape(c)}</td>" for c in row) + "</tr>"
    return f"<table><tbody><tr>{hrow}</tr>{body}</tbody></table>"


def build_storage_xml() -> str:
    parts: list[str] = []
    parts.append(h1("Funnel & Journey Data Architecture Decision Document"))

    # --- 1. Context and problem statement (what and why) ---
    parts.append(h2("1. What and why"))
    parts.append(
        p(
            "Funnel analysis lets product managers define a sequence of in-app events (e.g. App Open → Search → Add to Cart → Checkout) "
            "and measure how many unique users or sessions complete each step within a configurable time window. "
            "Pulse needs to support both ad-hoc exploration (interactive iteration) and saved funnels with scheduled refresh (e.g. daily email reports)."
        )
    )
    parts.append(
        p(
            "Custom product events flow through Vector to S3 Parquet (per-project buckets). OTEL traces and logs go to ClickHouse. "
            "The existing funnel implementation reads from ClickHouse; we need to decide where funnel queries should read custom-event data from, "
            "how daily reports should be powered, and what the operational trade-offs are."
        )
    )
    parts.append(
        p(
            "Constraint: funnel computation requires full event timelines for every user across the selected date range — not just aggregates. "
            "Full-window scans are expensive at interactive latency. Priority was saved funnels and dashboard reads first."
        )
    )
    parts.append(
        pre(
            "Mobile SDKs (OTEL)\n"
            "  ├─ Traces/Logs → OTEL Collector → ClickHouse (otel_traces, otel_logs)\n"
            "  └─ Custom events → Vector → S3 Parquet (pulse-otel-{project_id}/vector-logs/...)\n"
        )
    )
    parts.append(hr())

    # --- 2. Options ---
    parts.append(h2("2. Options considered"))
    parts.append(h3("Approach A — ClickHouse incremental state (windowFunnelState)"))
    parts.append(
        p(
            "S3 remains the single source of truth. ClickHouse stores only pre-aggregated funnel state per saved funnel "
            "using windowFunnelState()/windowFunnelMerge(). Daily cron processes new data; report queries merge state blobs. "
            "Rejected: per-funnel DDL lifecycle and operational complexity."
        )
    )
    parts.append(h3("Approach B — Daily Athena → MySQL snapshots"))
    parts.append(
        p(
            "A daily cron runs a full-window Athena funnel query per saved funnel and stores aggregated step counts in MySQL. "
            "Ad-hoc exploration uses Athena in real time. Rejected: cost scales with funnel count; no shared ClickHouse read path for dashboard."
        )
    )
    parts.append(h3("Approach C — Spark → ClickHouse funnel_results"))
    parts.append(
        p(
            "A Spark job (Glue/EMR) reads S3 Parquet and computes funnel results. Results are written to ClickHouse otel.funnel_results. "
            "Two triggers: on-save (immediate job for new funnel) and daily (batch for all funnels). Dashboard reads from ClickHouse via API."
        )
    )
    parts.append(hr())

    # --- 3. Recommendation ---
    parts.append(h2("3. Recommendation (Approach C)"))
    parts.append(
        pre(
            "1. User saves funnel → pulse-server → MySQL + trigger Spark (async)\n"
            "   Spark reads S3 Parquet → writes otel.funnel_results\n"
            "2. Daily cron → Spark all funnels → single-pass per project → funnel_results\n"
            "3. Dashboard → GET .../results → ClickHouse funnel_results only\n"
        )
    )
    parts.append(
        table_html(
            ("Component", "Role"),
            [
                ("pulse-server", "Persist funnel + funnel_job; trigger Spark; APIs for results + async explore."),
                ("Spark (on-save)", "One funnel; date window; write funnel_results."),
                ("Spark (daily)", "All funnels; max(date_range_days) I/O per project; write funnel_results."),
                ("ClickHouse", "otel.funnel_results pre-computed rows only."),
                ("S3 Parquet", "Raw custom events — Spark input."),
            ],
        )
    )
    parts.append(
        table_html(
            ("Event", "Action"),
            [
                ("Funnel saved/updated", "MySQL row; on-save Spark; optional job-callback updates funnel_job."),
                ("Funnel deleted", "MySQL delete; optional purge funnel_results."),
                ("Daily tick", "Spark daily job; idempotent write per run_date."),
            ],
        )
    )
    parts.append(
        p(
            "Trade-offs: new Glue/EMR ops; PySpark funnel logic; on-save latency mitigated with Computing… + job-status poll. "
            "Cost and deeper analysis: docs/architecture/funnel-data-architecture.md (§5)."
        )
    )
    parts.append(hr())

    # --- 4. Finalized approach (production) — last, after alternatives ---
    parts.append(h2("4. Finalized approach (production)"))
    parts.append(
        p(
            "The following captures the current product and technical contract for saved funnels. "
            "Detailed DDL: Schema Design page; Spark runtime: Spark job plan page."
        )
    )
    parts.append(h3("Product"))
    parts.append(
        table_html(
            ("Topic", "Decision"),
            [
                (
                    "Saved funnels",
                    "Steps + predefined filters (e.g. city, network provider, OS version) in MySQL; applied in Spark over S3 Parquet.",
                ),
                (
                    "Conversion window",
                    "Static per funnel: window_seconds at save time; not overridden per dashboard read.",
                ),
                (
                    "Pre-computed metrics",
                    "Spark (Glue/EMR) for all saved funnels and projects: daily batch + on-save for one funnel.",
                ),
                (
                    "Storage",
                    "ClickHouse otel.funnel_results only for aggregates; pulse-server GET /v1/funnel/{id}/results — no raw scan at read time.",
                ),
                (
                    "Explore (on-the-fly)",
                    "Async: 202 + job id + poll (see API doc); separate path from saved funnel_results unless explicitly cached.",
                ),
            ],
        )
    )
    parts.append(h3("Technical"))
    parts.append(
        "<ul>"
        "<li><strong>Source data:</strong> S3 Parquet — pulse-otel-{project_id}/vector-logs/...</li>"
        "<li><strong>Spark:</strong> Single-pass read per project where possible; column pruning; filters_json + steps.</li>"
        "<li><strong>ClickHouse:</strong> funnel_results only — do not create otel.product_events or S3Queue→custom_events for this pipeline.</li>"
        "<li><strong>MySQL:</strong> funnel, funnel_job (V9 migration).</li>"
        "</ul>"
    )
    parts.append(h3("References"))
    parts.append(
        "<ul>"
        '<li><a href="https://dream11.atlassian.net/wiki/spaces/Pulse/pages/4787011590">Schema Design</a> — MySQL + CH DDL + S3 input layout</li>'
        '<li><a href="https://dream11.atlassian.net/wiki/spaces/Pulse/pages/4785078289">Funnel &amp; User Journey API</a></li>'
        '<li><a href="https://dream11.atlassian.net/wiki/spaces/Pulse/pages/4782587990">Spark implementation (job plan)</a></li>'
        "<li>Repo: funnel-data-architecture.md, funnel-finalized-approach.md, funnel-spark-implementation-plan.md, funnel-server-apis.md</li>"
        "<li>CH DDL: backend/ingestion/clickhouse-funnel-results-schema.sql</li>"
        "</ul>"
    )
    parts.append(
        "<p>"
        "S3Queue streaming of raw events into ClickHouse is not the chosen production path; see funnel-s3queue-streaming.md for alternatives only. "
        'The former standalone page <a href="https://dream11.atlassian.net/wiki/spaces/Pulse/pages/4791042087">'
        "Funnel — Finalized approach (Spark + ClickHouse)</a> is deprecated; content lives here."
        "</p>"
    )

    body = "\n".join(parts)
    return (
        '<div xmlns="http://www.w3.org/1999/xhtml" '
        'xmlns:ac="http://www.atlassian.com/schema/confluence/4/ac/" '
        'xmlns:ri="http://www.atlassian.com/schema/confluence/4/ri/">'
        f"{body}</div>"
    )


def main() -> None:
    sys.stdout.write(build_storage_xml())


if __name__ == "__main__":
    main()
