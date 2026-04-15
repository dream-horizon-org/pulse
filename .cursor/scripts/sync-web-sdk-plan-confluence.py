#!/usr/bin/env python3
"""
Create or update Confluence child pages under the Web SDK Plan parent,
one page per markdown file in pulse-web-otel/web-sdk-plan/.

Requires the same env vars as atlassian-mcp.sh (see .cursor/.env.example):
  CONFLUENCE_URL, CONFLUENCE_USERNAME, CONFLUENCE_API_TOKEN

Optional:
  CONFLUENCE_WEB_SDK_PARENT_ID — defaults to the Pulse space parent page id.

Usage (from repo root):
  set -a && source .cursor/.env && set +a && python3 .cursor/scripts/sync-web-sdk-plan-confluence.py
"""

from __future__ import annotations

import base64
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

try:
    import markdown
except ImportError:
    print("Install dependencies: pip install markdown", file=sys.stderr)
    sys.exit(1)

REPO_ROOT = Path(__file__).resolve().parents[2]
PLAN_DIR = REPO_ROOT / "pulse-web-otel" / "web-sdk-plan"
DEFAULT_PARENT_ID = "4851302417"


def strip_yaml_front_matter(text: str) -> str:
    if not text.startswith("---\n"):
        return text
    end = text.find("\n---\n", 4)
    if end == -1:
        return text
    return text[end + 5 :].lstrip("\n")


def md_to_storage_html(md_text: str) -> str:
    md_text = strip_yaml_front_matter(md_text)
    return markdown.markdown(
        md_text,
        extensions=[
            "markdown.extensions.tables",
            "markdown.extensions.fenced_code",
            "markdown.extensions.nl2br",
            "markdown.extensions.sane_lists",
        ],
    )


def confluence_base_url() -> str:
    raw = os.environ.get("CONFLUENCE_URL", "").rstrip("/")
    if not raw:
        raise SystemExit("CONFLUENCE_URL is not set")
    return raw


def auth_header() -> str:
    user = os.environ.get("CONFLUENCE_USERNAME", "")
    token = os.environ.get("CONFLUENCE_API_TOKEN", "")
    if not user or not token:
        raise SystemExit("CONFLUENCE_USERNAME and CONFLUENCE_API_TOKEN must be set")
    creds = f"{user}:{token}".encode()
    return "Basic " + base64.b64encode(creds).decode()


def api_request(method: str, path: str, body: dict | None = None) -> dict:
    url = f"{confluence_base_url()}/rest/api{path}"
    data = None
    headers = {
        "Authorization": auth_header(),
        "Accept": "application/json",
        "Content-Type": "application/json",
    }
    if body is not None:
        data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            return json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        err = e.read().decode()
        raise SystemExit(f"HTTP {e.code} {method} {path}: {err}") from e


def find_child_by_title(parent_id: str, title: str) -> str | None:
    esc = title.replace("\\", "\\\\").replace('"', '\\"')
    q = f'type=page AND space=Pulse AND title="{esc}"'
    path = (
        "/content/search?cql="
        + urllib.parse.quote(q)
        + "&limit=10&expand=ancestors"
    )
    out = api_request("GET", path)
    for r in out.get("results", []):
        ancestors = r.get("ancestors", [])
        ids = [str(a.get("id")) for a in ancestors]
        if str(parent_id) in ids:
            return str(r.get("id"))
    return None


def create_page(parent_id: str, title: str, html: str) -> dict:
    body = {
        "type": "page",
        "title": title[:250],
        "ancestors": [{"id": parent_id}],
        "space": {"key": "Pulse"},
        "body": {
            "storage": {
                "value": html,
                "representation": "storage",
            }
        },
    }
    return api_request("POST", "/content", body)


def update_page(page_id: str, version: int, title: str, html: str) -> dict:
    body = {
        "version": {"number": version + 1},
        "title": title[:250],
        "type": "page",
        "body": {
            "storage": {
                "value": html,
                "representation": "storage",
            }
        },
    }
    return api_request("PUT", f"/content/{page_id}", body)


def get_page(page_id: str) -> dict:
    return api_request(
        "GET",
        f"/content/{page_id}?expand=body.storage,version",
    )


def main() -> None:
    if not PLAN_DIR.is_dir():
        raise SystemExit(f"Missing folder: {PLAN_DIR}")

    parent_id = os.environ.get("CONFLUENCE_WEB_SDK_PARENT_ID", DEFAULT_PARENT_ID)
    files = sorted(PLAN_DIR.glob("*.md"))
    if not files:
        raise SystemExit(f"No .md files in {PLAN_DIR}")

    print(f"Parent page id: {parent_id}  ({len(files)} markdown files)\n")

    for path in files:
        title = path.stem
        md_text = path.read_text(encoding="utf-8")
        html = md_to_storage_html(md_text)
        existing = find_child_by_title(parent_id, title)
        try:
            if existing:
                pg = get_page(existing)
                ver = int(pg.get("version", {}).get("number", 1))
                update_page(existing, ver, title, html)
                print(f"  updated: {title}")
            else:
                create_page(parent_id, title, html)
                print(f"  created: {title}")
        except SystemExit as e:
            print(f"  FAILED {title}: {e}", file=sys.stderr)
            raise


if __name__ == "__main__":
    main()
