#!/usr/bin/env python3
"""
Build Confluence *storage* XML for the Funnel & User Journey Schema Design page.

Page ID: 4787011590 — **all** MySQL / ClickHouse / S3 layout DDL and column contracts live here.
Spark *job behavior* only: https://dream11.atlassian.net/wiki/spaces/Pulse/pages/4782587990

Uses native Code macros for SQL blocks.

Usage:
  python3 docs/confluence-drafts/generate_funnel_schema_storage.py > /tmp/funnel-schema-storage.xml
  # MCP confluence_update_page(..., content_format="storage")
"""

from __future__ import annotations

import sys
import uuid
from xml.sax.saxutils import escape


def code_macro(sql: str, lang: str = "sql") -> str:
    mid = str(uuid.uuid4())
    safe = sql.replace("]]>", "]]]]><![CDATA[>")
    return (
        f'<ac:structured-macro ac:name="code" ac:schema-version="1" ac:macro-id="{mid}">\n'
        f'<ac:parameter ac:name="language">{lang}</ac:parameter>\n'
        f"<ac:plain-text-body><![CDATA[{safe}]]></ac:plain-text-body>\n"
        "</ac:structured-macro>"
    )


def p(text: str) -> str:
    return f"<p>{escape(text)}</p>"


def h1(t: str) -> str:
    return f"<h1>{escape(t)}</h1>"


def h2(t: str) -> str:
    return f"<h2>{escape(t)}</h2>"


def hr() -> str:
    return "<hr/>"


def table_html(headers: tuple[str, ...], rows: list[tuple[str, ...]]) -> str:
    hrow = "".join(f"<th>{escape(h)}</th>" for h in headers)
    body = ""
    for row in rows:
        body += "<tr>" + "".join(f"<td>{escape(c)}</td>" for c in row) + "</tr>"
    return f"<table><tbody><tr>{hrow}</tr>{body}</tbody></table>"


# Implemented today — keep in sync with V9__create_funnel_and_funnel_job_tables.sql
FUNNEL_V9_SQL = """CREATE TABLE IF NOT EXISTS funnel (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    funnel_id         VARCHAR(64)  NOT NULL UNIQUE COMMENT 'External ID e.g. UUID',
    project_id        VARCHAR(64)  NOT NULL COMMENT 'Project (proj-xxx)',
    name              VARCHAR(255) NOT NULL COMMENT 'Display name',
    steps_json        JSON         NOT NULL COMMENT 'Array of { eventName, dataType?, stepFilters? }',
    window_seconds    BIGINT       NOT NULL DEFAULT 86400 COMMENT 'Funnel window in seconds',
    mode              VARCHAR(32)  NOT NULL DEFAULT 'UNIQUE_USERS' COMMENT 'UNIQUE_USERS | SESSIONS',
    date_range_days   INT          NOT NULL DEFAULT 7 COMMENT 'Lookback days for Spark (e.g. 7 or 30)',
    filters_json      JSON         NULL COMMENT 'Global filters (same shape as FunnelRequest.filters)',
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by        VARCHAR(255) NULL,

    CONSTRAINT fk_funnel_project FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE,
    INDEX idx_funnel_project (project_id),
    INDEX idx_funnel_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Saved funnel definitions for Spark computation and dashboard';"""

FUNNEL_JOB_V9_SQL = """CREATE TABLE IF NOT EXISTS funnel_job (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    funnel_id      VARCHAR(64)  NOT NULL COMMENT 'References funnel.funnel_id',
    job_id         VARCHAR(255) NULL COMMENT 'Glue/EMR job run id',
    status         VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING | RUNNING | SUCCEEDED | FAILED',
    run_date       DATE         NULL COMMENT 'Date of data computed',
    error_message  TEXT         NULL,
    started_at     TIMESTAMP    NULL,
    completed_at   TIMESTAMP    NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_funnel_job_funnel FOREIGN KEY (funnel_id) REFERENCES funnel(funnel_id) ON DELETE CASCADE,
    INDEX idx_funnel_job_funnel (funnel_id),
    INDEX idx_funnel_job_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='On-save Spark job status for UI polling (Computing... / done / failed)';"""

# Target journey model (not yet in repo migrations)
JOURNEY_SQL = """CREATE TABLE IF NOT EXISTS journey (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    journey_id VARCHAR(64) NOT NULL UNIQUE,
    project_id VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    anchor_event_name VARCHAR(255) NOT NULL,
    anchor_type VARCHAR(32) NOT NULL,
    anchor_filters JSON NULL,
    direction VARCHAR(16) NOT NULL DEFAULT 'FORWARD',
    depth INT NOT NULL DEFAULT 5,
    date_range_days INT NOT NULL DEFAULT 7,
    thresholds JSON NOT NULL,
    filters JSON NULL,
    tags JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NULL,
    updated_by VARCHAR(255) NULL,
    is_archived TINYINT(1) NOT NULL DEFAULT 0,
    CONSTRAINT fk_journey_project FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE,
    INDEX idx_journey_project (project_id),
    INDEX idx_journey_project_active (project_id, is_archived),
    INDEX idx_journey_updated (updated_at)
);"""

CH_SQL = """CREATE TABLE IF NOT EXISTS otel.funnel_results
(
    funnel_id      String        COMMENT 'Same as MySQL funnel.funnel_id',
    project_id     String        COMMENT 'Project ID (proj-xxx)',
    run_date       Date          COMMENT 'Date of the data window (report date)',
    step_index     UInt8         COMMENT '0-based step index',
    step_name      String        COMMENT 'Event name for this step',
    user_count     UInt64        COMMENT 'Unique users (or sessions) reaching this step',
    conversion_pct Float64       COMMENT 'Conversion % from step 0 to this step',
    created_at     DateTime64(3) DEFAULT now64(3),
    CONSTRAINT chk_step_index CHECK step_index < 32
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(run_date)
ORDER BY (funnel_id, run_date, step_index)
SETTINGS index_granularity = 8192;"""


def build_storage_xml() -> str:
    ch_rows = [
        ("funnel_id", "String", "Same as MySQL funnel.funnel_id"),
        ("project_id", "String", "Project scope"),
        ("run_date", "Date", "Report date"),
        ("step_index", "UInt8", "0-based step"),
        ("step_name", "String", "Event name"),
        ("user_count", "UInt64", "Users/sessions at step"),
        ("conversion_pct", "Float64", "% from step 0"),
    ]
    ch_table = "<table><tbody><tr><th>Column</th><th>Type</th><th>Description</th></tr>"
    for c, t, d in ch_rows:
        ch_table += f"<tr><td>{escape(c)}</td><td>{escape(t)}</td><td>{escape(d)}</td></tr>"
    ch_table += "</tbody></table>"

    flow_rows = [
        ("MySQL funnel, funnel_job, journey", "Definitions and on-save job status"),
        ("S3 Parquet (Vector)", "Raw custom/product events — sole input to Spark for saved funnels"),
        ("Spark job", "Reads MySQL + S3 → writes aggregates only"),
        ("ClickHouse otel.funnel_results", "Pre-computed metrics; API read path"),
    ]
    flow_html = "<table><tbody><tr><th>Component</th><th>Role</th></tr>"
    for c, d in flow_rows:
        flow_html += f"<tr><td>{escape(c)}</td><td>{escape(d)}</td></tr>"
    flow_html += "</tbody></table>"

    s3_rows = [
        ("event_name", "string", "Logical event / step name"),
        ("project_id", "string", "Tenant / project"),
        ("user_id", "string", "Identity when mode = UNIQUE_USERS"),
        ("session_id", "string", "Identity when mode = SESSIONS"),
        ("timestamp", "timestamp", "Event time (timezone as stored)"),
        ("(dimensions)", "varies", "Columns used by filters_json — align names with Parquet"),
    ]
    s3_html = "<table><tbody><tr><th>Column</th><th>Type</th><th>Notes</th></tr>"
    for c, t, d in s3_rows:
        s3_html += f"<tr><td>{escape(c)}</td><td>{escape(t)}</td><td>{escape(d)}</td></tr>"
    s3_html += "</tbody></table>"

    parts: list[str] = []
    parts.append(h1("Funnel & User Journey Schema Design"))
    parts.append(h2("Overview"))
    parts.append(p("This page is the single source of truth for database and data-layout contracts for funnels and journeys."))
    parts.append(
        "<p>"
        "<strong>Spark job design</strong> (modes, reads, writes — no DDL): "
        '<a href="https://dream11.atlassian.net/wiki/spaces/Pulse/pages/4782587990">'
        "Funnel Spark implementation (job plan)</a>. "
        "<strong>REST APIs:</strong> "
        '<a href="https://dream11.atlassian.net/wiki/spaces/Pulse/pages/4785078289">'
        "Funnel &amp; User Journey API</a>. "
        "Repo: <code>docs/architecture/funnel-mysql-clickhouse-schema.md</code>, "
        "<code>backend/ingestion/clickhouse-funnel-results-schema.sql</code>, "
        "migration <code>V9__create_funnel_and_funnel_job_tables.sql</code>."
        "</p>"
    )
    parts.append(
        p(
            "Funnel definitions and job status live in MySQL. ClickHouse holds only otel.funnel_results "
            "(aggregates). Raw events are not mirrored into ClickHouse for this pipeline — they stay in S3 Parquet."
        )
    )
    parts.append(
        p(
            "Do not create otel.product_events (or an S3Queue → custom_events mirror) for saved-funnel computation."
        )
    )
    parts.append(hr())

    parts.append(h2("MySQL — funnel (implemented)"))
    parts.append(
        p(
            "Migration: backend/server/src/main/resources/db/migration/"
            "V9__create_funnel_and_funnel_job_tables.sql"
        )
    )
    parts.append(code_macro(FUNNEL_V9_SQL))
    parts.append(
        p(
            "Future migrations may add columns (e.g. description, tags, is_archived). "
            "Product target shapes are described in docs/architecture/funnel-mysql-clickhouse-schema.md."
        )
    )
    parts.append(hr())

    parts.append(h2("MySQL — funnel_job (on-save job status)"))
    parts.append(
        p(
            "One row per on-save job run; UI polls for Computing… / done / failed. "
            "Daily batch Spark status is not stored here — use Glue/cron logs."
        )
    )
    parts.append(code_macro(FUNNEL_JOB_V9_SQL))
    parts.append(hr())

    parts.append(h2("MySQL — journey (target)"))
    parts.append(p("Planned model for saved user journeys — not necessarily deployed yet."))
    parts.append(code_macro(JOURNEY_SQL))
    parts.append(hr())

    parts.append(h2("S3 Parquet — event input (Vector)"))
    parts.append(
        p(
            "Spark reads custom/product events from the existing Vector pipeline. "
            "Typical path pattern: s3://pulse-otel-{project_id}/vector-logs/.../*.parquet"
        )
    )
    parts.append(s3_html)
    parts.append(hr())

    parts.append(h2("ClickHouse — otel.funnel_results"))
    parts.append(
        p(
            "Only ClickHouse table for funnel *output* in this architecture. "
            "DDL file: backend/ingestion/clickhouse-funnel-results-schema.sql"
        )
    )
    parts.append(code_macro(CH_SQL))
    parts.append(ch_table)
    parts.append(hr())

    parts.append(h2("JSON shapes (summary)"))
    parts.append("<ul>")
    parts.append(
        "<li><strong>steps_json:</strong> array aligned with FunnelRequest.steps "
        "(eventName, dataType, pulseType, stepFilters).</li>"
    )
    parts.append(
        "<li><strong>filters_json / journey filters:</strong> field, operator, value[] — "
        "must map to Parquet columns where used in Spark.</li>"
    )
    parts.append("<li><strong>journey thresholds:</strong> minUserPercentage, minAbsoluteUsers, maxPathsToShow, collapseRepeated</li>")
    parts.append("</ul>")
    parts.append(hr())

    parts.append(h2("Data flow"))
    parts.append(flow_html)
    parts.append(hr())

    parts.append(h2("StepType / PulseType (explore vs Spark path)"))
    parts.append(
        p(
            "Ad-hoc OTEL explore: SCREEN → traces screen_load; INTERACTION → interaction; EVENT → logs/traces. "
            "Saved funnels over custom events: use Parquet columns above."
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
