#!/usr/bin/env python3
"""
RCA Report Generator
--------------------
Triggers RCA regeneration for all seeded interactions, polls until each
job completes, then prints a status summary.

Flow (mirrors useGetRcaReport.ts):
  POST /v1/ai/rca/report  { rcaType, entityKey, date, regenerate: true }
    HTTP 200 → report returned directly (cached or sync)
    HTTP 202 → async job; response body has jobId
  Poll GET /v1/ai-rca/job/{jobId}  every 3s
    status PENDING | PROCESSING → keep polling
    status COMPLETED            → done
    status FAILED               → fail

Usage:
    python3 deploy/scripts/rca-generate.py --token <JWT> [--project <id>] [--date <YYYY-MM-DD>]
    python3 deploy/scripts/rca-generate.py --token <JWT> --interaction app_launch
    RCA_TOKEN=<JWT> python3 deploy/scripts/rca-generate.py
"""

import argparse
import json
import os
import sys
import time
import urllib.request
import urllib.error
from datetime import date

# ── Config ────────────────────────────────────────────────────────────────────
DEFAULT_HOST    = "http://localhost:8080"
DEFAULT_PROJECT = "Testing1223-PmnxqFxx"
DEFAULT_DATE    = str(date.today())

POLL_INTERVAL_S = 3
JOB_TIMEOUT_S   = 180   # max wait per interaction

INTERACTIONS = [
    "app_launch",
    "home_feed_load",
    "product_search",
    "product_detail_view",
    "add_to_cart",
    "checkout_start",
    "payment_processing",
    "order_confirmation",
    "order_tracking",
    "category_browse",
    "image_gallery_load",
    "profile_update",
    "wishlist_add",
    "coupon_apply",
    "review_submit",
    "notifications_open",
    "deeplink_open",
]

# ── HTTP helpers ──────────────────────────────────────────────────────────────

def _headers(token, project_id):
    return {
        "Authorization": f"Bearer {token}",
        "X-Project-ID":  project_id,
        "Content-Type":  "application/json",
        "Accept":        "application/json",
    }


def _post(url, body, token, project_id):
    """Returns (http_status, parsed_body, error_str)."""
    data = json.dumps(body).encode()
    req  = urllib.request.Request(url, data=data, headers=_headers(token, project_id), method="POST")
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, json.loads(r.read()), None
    except urllib.error.HTTPError as e:
        body_text = e.read().decode(errors="replace")[:300]
        try:
            body_json = json.loads(body_text)
        except Exception:
            body_json = {"raw": body_text}
        return e.code, body_json, f"HTTP {e.code}"
    except Exception as e:
        return None, None, str(e)


def _get(url, token, project_id):
    """Returns (parsed_body, error_str)."""
    req = urllib.request.Request(url, headers=_headers(token, project_id))
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return json.loads(r.read()), None
    except urllib.error.HTTPError as e:
        return None, f"HTTP {e.code}: {e.read().decode(errors='replace')[:200]}"
    except Exception as e:
        return None, str(e)


def _extract_job_id(body):
    """Extract jobId from POST response body (handles wrapped/unwrapped shapes)."""
    if not body or not isinstance(body, dict):
        return None
    return (
        body.get("jobId")
        or body.get("data", {}).get("jobId")
    )


def _extract_job_status(body):
    """Extract status string from job poll response."""
    if not body or not isinstance(body, dict):
        return None
    status = (
        body.get("status")
        or body.get("data", {}).get("status")
    )
    return status.upper() if isinstance(status, str) else None


# ── Job polling ───────────────────────────────────────────────────────────────

def _poll_job(job_id, token, project_id, host, name):
    """
    Poll GET /v1/ai-rca/job/{jobId} (note: ai-rca not ai/rca) until terminal.
    Prints dots while waiting. Returns (final_status, error_str).
    """
    url      = f"{host}/v1/ai-rca/job/{job_id}"
    deadline = time.time() + JOB_TIMEOUT_S
    elapsed  = 0

    while time.time() < deadline:
        resp, err = _get(url, token, project_id)
        if err:
            return None, f"poll error: {err}"

        status = _extract_job_status(resp)
        if status == "COMPLETED":
            return "COMPLETED", None
        if status == "FAILED":
            msg = (
                (resp or {}).get("errorMessage")
                or (resp or {}).get("data", {}).get("errorMessage")
                or "job failed"
            )
            return "FAILED", msg
        if status == "UNKNOWN" or (status is not None and status not in ("PENDING", "PROCESSING")):
            return None, f"unexpected job status: {status}"

        # PENDING or PROCESSING — keep waiting
        sys.stdout.write(".")
        sys.stdout.flush()
        time.sleep(POLL_INTERVAL_S)
        elapsed += POLL_INTERVAL_S

    return None, f"timed out after {JOB_TIMEOUT_S}s"


# ── Single interaction generation ─────────────────────────────────────────────

OK   = "OK"
FAIL = "FAIL"

def generate_one(name, token, project_id, host, gen_date):
    """Trigger regeneration and wait for job completion. Returns (status_str, detail)."""
    url  = f"{host}/v1/ai/rca/report"
    body = {
        "rcaType":    "INTERACTION",
        "entityKey":  name,
        "date":       gen_date,
        "regenerate": True,
    }

    http_status, resp, err = _post(url, body, token, project_id)
    if err and http_status is None:
        return FAIL, err

    if http_status == 200:
        # Sync or cached — report returned immediately
        cached = (resp or {}).get("cached", False)
        return OK, "report returned directly" + (" (cached)" if cached else "")

    if http_status == 202:
        # Async job started — must poll until complete
        job_id = _extract_job_id(resp)
        if not job_id:
            return FAIL, f"202 response but no jobId in body: {str(resp)[:120]}"

        final_status, poll_err = _poll_job(job_id, token, project_id, host, name)
        sys.stdout.write(" ")  # space after dots
        if poll_err:
            return FAIL, f"job {job_id[:12]}…: {poll_err}"
        return OK, f"job {job_id[:12]}… → {final_status}"

    # Unexpected status
    return FAIL, f"HTTP {http_status}: {str(resp)[:120]}"


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="Trigger RCA regeneration for all seeded interactions")
    parser.add_argument("--token",       default=os.environ.get("RCA_TOKEN", ""),          help="Bearer JWT")
    parser.add_argument("--project",     default=os.environ.get("RCA_PROJECT", DEFAULT_PROJECT))
    parser.add_argument("--date",        default=os.environ.get("RCA_DATE", DEFAULT_DATE))
    parser.add_argument("--host",        default=DEFAULT_HOST)
    parser.add_argument("--interaction", default=None,                                      help="Single interaction name")
    args = parser.parse_args()

    if not args.token:
        print("ERROR: --token is required (or set RCA_TOKEN env var)", file=sys.stderr)
        sys.exit(1)

    targets = [args.interaction] if args.interaction else INTERACTIONS
    print(f"RCA Generate  |  host={args.host}  project={args.project}  date={args.date}")
    print(f"Generating {len(targets)} interaction(s)...\n")

    results = []
    for name in targets:
        sys.stdout.write(f"  → {name:<26} ")
        sys.stdout.flush()
        status, detail = generate_one(name, args.token, args.project, args.host, args.date)
        color = "\033[32m" if status == OK else "\033[31m"
        reset = "\033[0m"
        print(f"{color}{status}{reset}  {detail}")
        results.append((name, status, detail))

    # Summary
    sep = "─" * 70
    ok_count   = sum(1 for _, s, _ in results if s == OK)
    fail_count = sum(1 for _, s, _ in results if s == FAIL)

    print(f"\n{sep}")
    print(f"  Total: \033[32m{ok_count} ok\033[0m  \033[31m{fail_count} failed\033[0m")
    if fail_count:
        print(f"\n  Failed interactions:")
        for name, status, detail in results:
            if status == FAIL:
                print(f"    {name}: {detail}")
    print(sep)

    if fail_count:
        sys.exit(1)


if __name__ == "__main__":
    main()
