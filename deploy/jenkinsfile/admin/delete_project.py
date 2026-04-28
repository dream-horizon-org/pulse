#!/usr/bin/env python3
"""
Deletes all data for a single project from MySQL, ClickHouse, and OpenFGA.
Mirror of delete-project.sh — keep behavior in sync when changing SQL or steps.

Env: PROJECT_ID, DRY_RUN (true/false), MYSQL_* — required always.
Execute path also needs CH_ADMIN_* and OPENFGA_*.

Subcommand: python delete_project.py --mysql-preview  (MySQL unified table/count preview only; MYSQL_* + PROJECT_ID)
"""
from __future__ import annotations

import os
import re
import sys
from typing import Any

import pymysql
import requests

# --- logging (ANSI, same spirit as shell) ---------------------------------
def _info(msg: str) -> None:
    print(f"\033[0;34m[INFO]\033[0m  {msg}")


def _ok(msg: str) -> None:
    print(f"\033[0;32m[ OK ]\033[0m  {msg}")


def _warn(msg: str) -> None:
    print(f"\033[1;33m[WARN]\033[0m  {msg}")


def _err(msg: str) -> None:
    print(f"\033[0;31m[ERR ]\033[0m  {msg}", file=sys.stderr)


def _step(msg: str) -> None:
    print(f"\n\033[0;35m── {msg} ──\033[0m")


def _must(name: str) -> str:
    v = os.environ.get(name)
    if not v:
        _err(f"{name} must be set")
        sys.exit(1)
    return v


def _dry_run() -> bool:
    return os.environ.get("DRY_RUN", "true").lower() in ("1", "true", "yes")


def _split_mysql_statements(sql: str) -> list[str]:
    statements: list[str] = []
    buf: list[str] = []
    for line in sql.splitlines():
        st = line.strip()
        if not st:
            continue
        if st.startswith("--"):
            continue
        buf.append(line)
        if st.endswith(";"):
            block = "\n".join(buf).strip()
            if block:
                statements.append(block)
            buf = []
    if buf:
        raise ValueError("Unclosed SQL fragment (missing ';' on last line)")
    return statements


def _build_ch_identifiers(project_id: str) -> tuple[str, str, str]:
    sanitized = project_id.replace("-", "_")
    if sanitized.startswith("proj_"):
        sanitized = sanitized[5:]
    ch_user = f"project_{sanitized}"
    policy = f"policy_{sanitized}"
    cluster = os.environ.get("CH_CLUSTER_NAME", "").strip()
    # Match ClickhouseProjectConnectionPoolManager.getOnClusterClause(): cluster id must be quoted —
    # unquoted names with hyphens parse as subtraction (e.g. pulse-ch → pulse - ch).
    if cluster:
        esc = cluster.replace("'", "''")
        on_cluster = f" ON CLUSTER '{esc}'"
    else:
        on_cluster = ""
    return ch_user, policy, on_cluster


def _mysql_connect() -> pymysql.connections.Connection:
    return pymysql.connect(
        host=_must("MYSQL_HOST"),
        port=int(os.environ.get("MYSQL_PORT", "3306")),
        user=_must("MYSQL_USER"),
        password=_must("MYSQL_PASSWORD"),
        database=os.environ.get("MYSQL_DATABASE", "pulse_db"),
        charset="utf8mb4",
        autocommit=False,
    )


def _run_mysql_scalar(conn: pymysql.connections.Connection, sql: str) -> str | None:
    with conn.cursor() as cur:
        cur.execute(sql)
        row = cur.fetchone()
        if row is None:
            return None
        return str(row[0]) if row[0] is not None else None


def _mysql_fetch_fk_to_projects(conn: pymysql.connections.Connection) -> dict[str, str]:
    """TABLE_NAME -> DELETE_RULE for FK referencing projects(project_id)."""
    q = """
    SELECT DISTINCT kcu.TABLE_NAME, rc.DELETE_RULE
    FROM information_schema.REFERENTIAL_CONSTRAINTS rc
    INNER JOIN information_schema.KEY_COLUMN_USAGE kcu
      ON rc.CONSTRAINT_SCHEMA = kcu.CONSTRAINT_SCHEMA
     AND rc.TABLE_NAME = kcu.TABLE_NAME
     AND rc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME
    WHERE rc.CONSTRAINT_SCHEMA = DATABASE()
      AND kcu.TABLE_SCHEMA = DATABASE()
      AND kcu.REFERENCED_TABLE_NAME = 'projects'
      AND kcu.REFERENCED_COLUMN_NAME = 'project_id'
    ORDER BY kcu.TABLE_NAME
    """
    out: dict[str, str] = {}
    try:
        with conn.cursor() as cur:
            cur.execute(q)
            for row in cur.fetchall() or []:
                out[str(row[0])] = str(row[1]) if row[1] is not None else "?"
    except Exception:
        pass
    return out


SCRIPT_TRANSACTION_TABLES: frozenset[str] = frozenset(
    {
        "alert_evaluation_history",
        "alert_scope",
        "alerts",
        "notification_channels_old",
        "event_attribute_definitions",
        "event_definitions",
        "usage_limit_notifications",
        "rca_report_cache",
        "rca_report_jobs",
        "analytics_jobs",
        "funnel_journey_tag",
        "funnel",
        "journey",
        "projects",
    },
)


def _preview_row_count(conn: pymysql.connections.Connection, project_id: str, table: str) -> str:
    try:
        with conn.cursor() as cur:
            if table == "alert_evaluation_history":
                cur.execute(
                    """
                    SELECT COUNT(*) FROM alert_evaluation_history aeh
                      INNER JOIN alert_scope sc ON aeh.scope_id = sc.id
                      INNER JOIN alerts a ON sc.alert_id = a.id
                    WHERE a.project_id = %s
                    """,
                    (project_id,),
                )
            elif table == "alert_scope":
                cur.execute(
                    """
                    SELECT COUNT(*) FROM alert_scope sc
                      INNER JOIN alerts a ON sc.alert_id = a.id
                    WHERE a.project_id = %s
                    """,
                    (project_id,),
                )
            elif table == "event_attribute_definitions":
                cur.execute(
                    """
                    SELECT COUNT(*) FROM event_attribute_definitions ead
                      INNER JOIN event_definitions ed ON ead.event_definition_id = ed.id
                    WHERE ed.project_id = %s
                    """,
                    (project_id,),
                )
            elif table == "analytics_jobs":
                # On-save jobs only (AnalyticsJobType.FUNNEL / JOURNEY). Batch types
                # (FUNNELS_DAILY, JOURNEYS_DAILY, EVENTS_INCREMENTAL) use reference_id NULL and are not per-project deletes.
                cur.execute(
                    """
                    SELECT COUNT(*) FROM analytics_jobs aj
                    WHERE (
                      (aj.job_type = 'FUNNEL'
                        AND EXISTS (
                          SELECT 1 FROM funnel f WHERE f.id = aj.reference_id AND f.project_id = %s))
                      OR
                      (aj.job_type = 'JOURNEY'
                        AND EXISTS (
                          SELECT 1 FROM journey j WHERE j.id = aj.reference_id AND j.project_id = %s))
                    )
                    """,
                    (project_id, project_id),
                )
            else:
                cur.execute(
                    f"SELECT COUNT(*) FROM `{table}` WHERE project_id = %s",
                    (project_id,),
                )
            row = cur.fetchone()
            return str(row[0]) if row else "?"
    except Exception:
        return "?"


def _sources_tag(fk_dr: str | None, in_script: bool) -> str:
    segments: list[str] = []
    if fk_dr is not None:
        segments.append(f"FK:{fk_dr}")
    if in_script:
        segments.append("scriptTxn")
    return "+".join(segments) if segments else "—"


def print_mysql_dry_preview_all_tables(conn: pymysql.connections.Connection, project_id: str) -> None:
    """Single listing: FK children via INFORMATION_SCHEMA merged with scripted DELETE targets."""
    fk_map = _mysql_fetch_fk_to_projects(conn)
    unified = sorted(set(fk_map.keys()) | SCRIPT_TRANSACTION_TABLES)

    _step(
        "MySQL — rows removed per table (dry run; INFORMATION_SCHEMA FKs + scripted transaction)"
    )

    rows_out: list[tuple[str, str, str]] = []
    for tbl in unified:
        tag = _sources_tag(fk_map.get(tbl), tbl in SCRIPT_TRANSACTION_TABLES)
        cnt = _preview_row_count(conn, project_id, tbl)
        rows_out.append((tbl, cnt, tag))

    print()
    print(f"{'table':<46} {'rows':>8}   source")
    print(f"{'':46} {'':>8}   FK = FK to projects(project_id); scriptTxn = scripted DELETE txn")
    print("  " + "-" * 88)
    for tbl, cnt, tag in sorted(rows_out, key=lambda x: x[0]):
        print(f"  {tbl:<44} {cnt:>8}   [{tag}]")
    print()


def _ch_post(query: str) -> None:
    if _dry_run():
        _info(f"[DRY RUN] ClickHouse: {query}")
        return
    host = _must("CH_ADMIN_HOST")
    port = int(os.environ.get("CH_ADMIN_PORT", "8123"))
    u = _must("CH_ADMIN_USER")
    p = _must("CH_ADMIN_PASSWORD")
    url = f"http://{host}:{port}/"
    r = requests.post(
        url,
        data=query.encode("utf-8"),
        auth=(u, p),
        timeout=300,
    )
    if r.status_code >= 400:
        _err(f"ClickHouse DDL failed: {query}")
        _err(r.text)
        raise SystemExit(1)
    _info(f"ClickHouse: {query}")


def _openfga_read_write_delete(object_key: str) -> None:
    base = _must("OPENFGA_API_URL").rstrip("/")
    store = _must("OPENFGA_STORE_ID")
    read_url = f"{base}/stores/{store}/read"
    write_url = f"{base}/stores/{store}/write"
    page_token = ""
    total = 0
    while True:
        body: dict[str, Any] = {"tuple_key": {"object": object_key}}
        if page_token:
            body["continuation_token"] = page_token
        r = requests.post(read_url, json=body, timeout=120)
        if r.status_code >= 400:
            _err(f"OpenFGA read failed for object: {object_key}")
            sys.exit(1)
        data = r.json()
        tuples = data.get("tuples") or []
        keys = []
        for t in tuples:
            k = t.get("key", {})
            keys.append(
                {
                    "user": k.get("user"),
                    "relation": k.get("relation"),
                    "object": k.get("object"),
                }
            )
        count = len(keys)
        if count > 0:
            if _dry_run():
                _info(
                    f"[DRY RUN] Would delete {count} OpenFGA tuple(s) for {object_key}:"
                )
                for tk in keys:
                    print(
                        f"    {tk.get('user')}  --[{tk.get('relation')}]-->  {tk.get('object')}"
                    )
            else:
                w = requests.post(
                    write_url,
                    json={"deletes": {"tuple_keys": keys}},
                    timeout=120,
                )
                if w.status_code >= 400:
                    _err(f"OpenFGA delete failed for object: {object_key}")
                    sys.exit(1)
                _ok(f"Deleted {count} OpenFGA tuple(s) for {object_key}")
            total += count
        page_token = (data.get("continuation_token") or "") or ""
        if not page_token or page_token == "null":
            break
    if total == 0:
        _warn(f"No OpenFGA tuples found for {object_key}")


def _project_delete_sql(project_id: str) -> str:
    # Kept in sync with delete-project.sh (MySQL heredoc)
    return f"""
START TRANSACTION;

DROP TEMPORARY TABLE IF EXISTS tmp_cleanup_projects;
CREATE TEMPORARY TABLE tmp_cleanup_projects (project_id VARCHAR(64) NOT NULL PRIMARY KEY);
INSERT INTO tmp_cleanup_projects VALUES ('{project_id}');

DELETE aeh FROM alert_evaluation_history aeh
  INNER JOIN alert_scope sc ON aeh.scope_id = sc.id
  INNER JOIN alerts a ON sc.alert_id = a.id
  INNER JOIN tmp_cleanup_projects t ON t.project_id = a.project_id;

DELETE sc FROM alert_scope sc
  INNER JOIN alerts a ON sc.alert_id = a.id
  INNER JOIN tmp_cleanup_projects t ON t.project_id = a.project_id;

DELETE a FROM alerts a
  INNER JOIN tmp_cleanup_projects t ON t.project_id = a.project_id;

DELETE nco FROM notification_channels_old nco
  INNER JOIN tmp_cleanup_projects t ON nco.project_id = t.project_id;

DELETE ead FROM event_attribute_definitions ead
  INNER JOIN event_definitions ed ON ead.event_definition_id = ed.id
  INNER JOIN tmp_cleanup_projects t ON ed.project_id = t.project_id;

DELETE ed FROM event_definitions ed
  INNER JOIN tmp_cleanup_projects t ON ed.project_id = t.project_id;

DELETE uln FROM usage_limit_notifications uln
  INNER JOIN tmp_cleanup_projects t ON uln.project_id = t.project_id;

DELETE FROM rca_report_cache  WHERE project_id IN (SELECT project_id FROM tmp_cleanup_projects);
DELETE FROM rca_report_jobs   WHERE project_id IN (SELECT project_id FROM tmp_cleanup_projects);

DELETE aj FROM analytics_jobs aj
  INNER JOIN funnel f ON aj.reference_id = f.id
  INNER JOIN tmp_cleanup_projects t ON f.project_id = t.project_id
  WHERE aj.job_type = 'FUNNEL';

DELETE aj FROM analytics_jobs aj
  INNER JOIN journey j ON aj.reference_id = j.id
  INNER JOIN tmp_cleanup_projects t ON j.project_id = t.project_id
  WHERE aj.job_type = 'JOURNEY';

DELETE fjt FROM funnel_journey_tag fjt
  INNER JOIN tmp_cleanup_projects t ON fjt.project_id = t.project_id;

DELETE FROM funnel   WHERE project_id IN (SELECT project_id FROM tmp_cleanup_projects);
DELETE FROM journey  WHERE project_id IN (SELECT project_id FROM tmp_cleanup_projects);

DELETE p FROM projects p
  INNER JOIN tmp_cleanup_projects t ON p.project_id = t.project_id;

DROP TEMPORARY TABLE IF EXISTS tmp_cleanup_projects;
COMMIT;
"""


def _cli_mysql_preview() -> None:
    """Print unified MySQL table list + row counts (DRY RUN). Requires PROJECT_ID + MYSQL_* only."""
    project_id = _must("PROJECT_ID")
    if not re.match(r"^[a-zA-Z0-9_-]+$", project_id):
        _err(f"PROJECT_ID contains invalid characters: {project_id}")
        sys.exit(1)

    conn = _mysql_connect()
    try:
        name = _run_mysql_scalar(
            conn,
            f"SELECT name FROM projects WHERE project_id = '{project_id}' LIMIT 1",
        )
        if not name:
            _err(f"Project not found in MySQL: {project_id}")
            sys.exit(1)
        print_mysql_dry_preview_all_tables(conn, project_id)
    finally:
        conn.close()


def main() -> None:
    project_id = _must("PROJECT_ID")
    if not re.match(r"^[a-zA-Z0-9_-]+$", project_id):
        _err(f"PROJECT_ID contains invalid characters: {project_id}")
        sys.exit(1)

    ch_user, ch_policy, on_cluster = _build_ch_identifiers(project_id)
    if not _dry_run():
        for env_var in (
            "CH_ADMIN_HOST",
            "CH_ADMIN_USER",
            "CH_ADMIN_PASSWORD",
            "OPENFGA_API_URL",
            "OPENFGA_STORE_ID",
        ):
            _must(env_var)

    dry = "true" if _dry_run() else "false"
    print()
    print("╔══════════════════════════════════════════════════════════╗")
    print(f"║  Delete Project  (python)  •  DRY_RUN={dry}")
    print("╚══════════════════════════════════════════════════════════╝")

    conn = _mysql_connect()
    try:
        name = _run_mysql_scalar(
            conn,
            f"SELECT name FROM projects WHERE project_id = '{project_id}' LIMIT 1",
        )
        if not name:
            _err(f"Project not found in MySQL: {project_id}")
            sys.exit(1)
        tid = _run_mysql_scalar(
            conn,
            f"SELECT tenant_id FROM projects WHERE project_id = '{project_id}' LIMIT 1",
        )
        is_active = _run_mysql_scalar(
            conn,
            f"SELECT is_active FROM projects WHERE project_id = '{project_id}' LIMIT 1",
        )
        print()
        _info(f"Project:      {name}  ({project_id})")
        _info(f"Tenant:       {tid}")
        _info(f"Active:       {is_active}")
        _info(f"CH username:  {ch_user}")
        _info(f"CH policy:    {ch_policy}")
        print()
        print_mysql_dry_preview_all_tables(conn, project_id)

        print()
    finally:
        conn.close()

    if _dry_run():
        print()
        _info("DRY RUN complete — no changes made.")
        _info("Re-run with DRY_RUN=false to execute the deletion.")
        return

    sql_blob = _project_delete_sql(project_id)
    stmts = _split_mysql_statements(sql_blob)
    _step("Step 1/3: MySQL")
    conn2 = _mysql_connect()
    try:
        with conn2.cursor() as cur:
            for st in stmts:
                if st.strip():
                    cur.execute(st)
        conn2.commit()
    except Exception:
        conn2.rollback()
        raise
    finally:
        conn2.close()
    _ok("MySQL cleanup complete")

    _step("Step 2/3: ClickHouse")
    _ch_post(f"DROP ROW POLICY IF EXISTS {ch_policy}{on_cluster} ON otel.*")
    _ch_post(f"DROP USER IF EXISTS {ch_user}{on_cluster}")
    _ok("ClickHouse cleanup complete")

    _step("Step 3/3: OpenFGA")
    _openfga_read_write_delete(f"project:{project_id}")
    _ok("OpenFGA cleanup complete")

    print()
    _ok("══════════════════════════════════════════════════════════")
    _ok(f"  Project deletion complete: {name} ({project_id})")
    _ok("══════════════════════════════════════════════════════════")


if __name__ == "__main__":
    try:
        if len(sys.argv) > 1 and sys.argv[1] == "--mysql-preview":
            _cli_mysql_preview()
        else:
            main()
    except KeyboardInterrupt:
        sys.exit(130)
