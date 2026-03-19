#!/usr/bin/env python3
"""
Confluence *storage* XML for: Funnel Spark implementation (job plan & runtime only).

Page ID: 4782587990

**No DDL here** — MySQL / ClickHouse schemas live only on:
  https://dream11.atlassian.net/wiki/spaces/Pulse/pages/4787011590

Run:
  python3 docs/confluence-drafts/generate_funnel_spark_implementation_storage.py > /tmp/funnel-spark-storage.xml
Then MCP confluence_update_page(..., content_format="storage").
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


def table_html(headers: tuple[str, ...], rows: list[tuple[str, ...]]) -> str:
    hrow = "".join(f"<th>{escape(h)}</th>" for h in headers)
    body = ""
    for row in rows:
        body += "<tr>" + "".join(f"<td>{escape(c)}</td>" for c in row) + "</tr>"
    return f"<table><tbody><tr>{hrow}</tr>{body}</tbody></table>"


def build_storage_xml() -> str:
    parts: list[str] = []
    parts.append(h1("Funnel Spark implementation (job plan)"))
    parts.append(
        "<p>"
        "<strong>Schema source of truth:</strong> "
        '<a href="https://dream11.atlassian.net/wiki/spaces/Pulse/pages/4787011590">'
        "Funnel &amp; User Journey Schema Design</a> "
        "(MySQL <code>funnel</code>, <code>funnel_job</code>, <code>journey</code>; "
        "ClickHouse <code>otel.funnel_results</code>; S3 Parquet input layout). "
        "<strong>APIs:</strong> "
        '<a href="https://dream11.atlassian.net/wiki/spaces/Pulse/pages/4785078289">'
        "Funnel &amp; User Journey API</a>. "
        "Repo: <code>docs/architecture/funnel-spark-implementation-plan.md</code>, "
        "<code>funnel-spark-job-implementation-plan.md</code>, "
        "<code>funnel-spark-job-final-plan.md</code>."
        "</p>"
    )
    parts.append(
        p(
            "This page describes only how the Spark/Glue job runs: modes, arguments, S3 read strategy, "
            "funnel computation rules, and how results are written back. It does not duplicate DDL."
        )
    )
    parts.append(
        p(
            "Runtime summary: load funnel definitions from MySQL (columns per schema page); "
            "read custom/product events from S3 Parquet (Vector paths); compute ordered steps with "
            "window_seconds and filters; write aggregated rows to otel.funnel_results only — "
            "no raw-event ClickHouse table for this pipeline."
        )
    )
    parts.append(
        "<p><strong>Product rules (Spark-relevant):</strong> "
        "Apply global <code>filters_json</code> and per-step filters in the job; "
        "<code>window_seconds</code> is static per funnel. "
        "Explore / on-the-fly analysis is async via API — out of scope for the saved-funnel Spark path.</p>"
    )
    parts.append(hr())

    parts.append(h2("1. Job modes"))
    parts.append(
        table_html(
            ("Mode", "Arguments", "Behavior"),
            [
                (
                    "on_save",
                    "mode, funnel_id, project_id, date_from, date_to",
                    "Single funnel; run_date typically = date_to; replace existing rows for "
                    "(funnel_id, run_date) then insert new metrics.",
                ),
                (
                    "daily",
                    "mode, run_date",
                    "Load all saved funnels (MySQL or internal); group by project_id; "
                    "one Parquet read per project using max(date_range_days); "
                    "emit all funnels for that run_date.",
                ),
            ],
        )
    )

    parts.append(h2("2. Read strategy (S3 Parquet)"))
    parts.append(
        table_html(
            ("Topic", "Decision"),
            [
                ("Reads", "One Spark read per project per run — not one read per funnel."),
                ("Time window", "Per project: max(date_range_days) across funnels for I/O; "
                 "then filter each funnel to its own range in memory."),
                ("Paths", "Convention: s3://pulse-otel-{project_id}/vector-logs/... (Parquet). "
                 "Exact layout on schema page."),
                ("Temporal", "Full-window read each run unless optimized later (chunking)."),
            ],
        )
    )

    parts.append(h2("3. Optimizations"))
    parts.append(
        "<ul>"
        "<li>Column pruning: base identity/timestamp/event columns + union of filter columns needed "
        "for that project’s funnels.</li>"
        "<li>Avoid <code>collect()</code> on full data; write ClickHouse from executors.</li>"
        "<li>Repartition large shuffles; optional time-chunked reads then aggregate.</li>"
        "</ul>"
    )

    parts.append(h2("4. End-to-end flows"))
    parts.append(
        "<p><strong>On-save:</strong> pulse-server persists rows (see schema page) and triggers Glue/EMR → "
        "Spark loads funnel row → reads S3 → computes → inserts into <code>otel.funnel_results</code> → "
        "internal <code>POST /v1/funnel/job-callback</code> updates job status. "
        "UI polls <code>GET /v1/funnel/&lt;id&gt;/job-status</code> or results.</p>"
    )
    parts.append(
        "<p><strong>Daily:</strong> scheduler passes <code>run_date</code> → Spark loads all funnels → "
        "per project one Parquet scan → per funnel compute → idempotent replace for that "
        "<code>run_date</code> (strategy on schema / ops doc).</p>"
    )
    parts.append(
        "<p><strong>Serving:</strong> <code>GET /v1/funnel/&lt;id&gt;/results</code> reads ClickHouse only — "
        "no Parquet scan at request time for saved funnels.</p>"
    )

    parts.append(h2("5. Funnel logic (implementation)"))
    parts.append(
        "<ul>"
        "<li>Identity column: <code>user_id</code> when mode is UNIQUE_USERS, else <code>session_id</code>.</li>"
        "<li>Ordered steps; enforce <code>window_seconds</code> between first and last step in the funnel.</li>"
        "<li>Emit one output row per step index: user_count, conversion_pct vs step 0.</li>"
        "</ul>"
    )

    parts.append(h2("6. ClickHouse write semantics"))
    parts.append(
        "<ul>"
        "<li>On-save: delete or replace partition slice for (funnel_id, run_date), then insert.</li>"
        "<li>Daily: delete by run_date (or equivalent) then bulk insert all funnels for that date.</li>"
        "<li>Use JDBC or native client from executors; batch to avoid driver OOM.</li>"
        "</ul>"
    )

    parts.append(h2("7. Phasing"))
    parts.append(
        table_html(
            ("Phase", "Scope"),
            [
                ("1", "On-save path; pruning; core funnel logic; CH write; callback + polling."),
                ("2", "Daily mode; multi-funnel; single read per project."),
                ("3", "Hardening: partial failures, observability, repartition tuning, optional chunking."),
            ],
        )
    )

    parts.append(h2("8. Checklist"))
    parts.append(
        table_html(
            ("Item", "How"),
            [
                ("Pruning", "Project column set = union of fields referenced in all funnels’ filters."),
                ("Single scan / project", "Filter per funnel after the shared DataFrame."),
                ("Idempotency", "Replace keys documented on schema page before insert."),
                ("Status", "On-save: MySQL funnel_job; daily: rely on Glue/cron logs."),
            ],
        )
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
