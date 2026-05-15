#!/usr/bin/env python3
"""
Deletes tenant data from MySQL and OpenFGA.

Env: TENANT_ID, DRY_RUN, MYSQL_*, OPENFGA_* (from .db-ops-env).
Requires no projects left for the tenant (delete projects first).
"""
from __future__ import annotations

import os
import re
import sys
from typing import Any

import pymysql
import requests


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
        _info(f"No OpenFGA tuples for {object_key}")


def main() -> None:
    tenant_id = _must("TENANT_ID")
    if not re.match(r"^[a-zA-Z0-9_-]+$", tenant_id):
        _err(f"TENANT_ID contains invalid characters: {tenant_id}")
        sys.exit(1)
    for name in ("OPENFGA_API_URL", "OPENFGA_STORE_ID"):
        _must(name)

    dry = "true" if _dry_run() else "false"
    print()
    print("╔══════════════════════════════════════════════════════════╗")
    print(f"║  Delete Tenant  (python)  •  DRY_RUN={dry}")
    print("╚══════════════════════════════════════════════════════════╝")

    conn = _mysql_connect()
    try:
        tname = _run_mysql_scalar(
            conn,
            f"SELECT name FROM tenants WHERE tenant_id = '{tenant_id}' LIMIT 1",
        )
        if not tname:
            _err(f"Tenant not found in MySQL: {tenant_id}")
            sys.exit(1)
        is_active = _run_mysql_scalar(
            conn,
            f"SELECT is_active FROM tenants WHERE tenant_id = '{tenant_id}' LIMIT 1",
        )
        print()
        _info(f"Tenant:  {tname}  ({tenant_id})")
        _info(f"Active:  {is_active}")
        print()
        _step("Pre-check: remaining projects for tenant")
        with conn.cursor() as cur:
            cur.execute(
                f"SELECT project_id, name FROM projects WHERE tenant_id = %s", (tenant_id,)
            )
            remaining = list(cur.fetchall() or [])
        if remaining:
            _err("Cannot delete tenant — the following projects still exist:")
            print()
            for row in remaining:
                pid, pname = row[0], row[1]
                print(f"    {pid}  ({pname})")
            print()
            _err(
                "Run the delete-project job (delete-project.py) for each project first, then retry."
            )
            sys.exit(1)
        _ok("No remaining projects — safe to proceed")
        print()
    finally:
        conn.close()

    if _dry_run():
        _info("DRY RUN — would delete:")
        _info(f"  MySQL: tnc_acceptances rows for {tenant_id}")
        _info(f"  MySQL: tenants row for {tenant_id}")
        _info(f"  OpenFGA: all tuples where object = tenant:{tenant_id}")
        print()
        _step("OpenFGA tuples (preview)")
        _openfga_read_write_delete(f"tenant:{tenant_id}")
        print()
        _info("DRY RUN complete — no changes made.")
        _info("Re-run with DRY_RUN=false to execute the deletion.")
        return

    _step("Step 1/2: MySQL")
    conn2 = _mysql_connect()
    try:
        with conn2.cursor() as cur:
            cur.execute("DELETE FROM tnc_acceptances WHERE tenant_id = %s", (tenant_id,))
            cur.execute("DELETE FROM tenants WHERE tenant_id = %s", (tenant_id,))
        conn2.commit()
    except Exception:
        conn2.rollback()
        raise
    finally:
        conn2.close()
    _ok(f"MySQL: deleted tenant row ({tenant_id})")

    _step("Step 2/2: OpenFGA")
    _openfga_read_write_delete(f"tenant:{tenant_id}")
    _ok("OpenFGA cleanup complete")

    print()
    _ok("══════════════════════════════════════════════════════════")
    _ok(f"  Tenant deletion complete: {tname} ({tenant_id})")
    _ok("══════════════════════════════════════════════════════════")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit(130)
